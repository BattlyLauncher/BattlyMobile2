package net.kdt.pojavlaunch.battlyworlds;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.AudioTrackSink;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SdpObserver;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BattlyWorldsVoiceManager implements BattlyWorldsRealtimeClient.Listener {
    interface Listener {
        void onVoiceChanged();
        void onVoiceError(String message);
    }

    static final int MICROPHONE_PERMISSION_REQUEST = 7290;
    private static final String TAG = "BattlyWorldsVoice";
    private static final BattlyWorldsVoiceManager INSTANCE = new BattlyWorldsVoiceManager();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ExecutorService VOICE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BattlyWorldsVoice");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, PeerConnection> peers = new HashMap<>();
    private final Map<String, AudioTrack> remoteTracks = new HashMap<>();
    private final Map<String, AudioTrackSink> remoteTrackSinks = new HashMap<>();
    private final Map<String, VoiceActivityDetector> voiceDetectors = new HashMap<>();
    private final Map<String, String> usernames = new HashMap<>();
    private final Set<String> speakingUsers = new HashSet<>();
    private final Set<String> connectedVoiceUsers = new HashSet<>();
    private final Map<String, List<IceCandidate>> pendingIceCandidates = new HashMap<>();
    private final Set<String> remoteDescriptionUsers = new HashSet<>();
    private final Set<String> locallySilenced = new HashSet<>();
    private final Map<String, Integer> userVolumes = new HashMap<>();
    private final Map<String, Runnable> peerRecoveries = new HashMap<>();
    private final List<PendingVoiceSignal> pendingVoiceSignals = new ArrayList<>();
    private final Handler recoveryHandler = new Handler(Looper.getMainLooper());
    private PeerConnectionFactory factory;
    private JavaAudioDeviceModule audioDeviceModule;
    private AudioSource audioSource;
    private AudioTrack localTrack;
    private Context appContext;
    private AudioManager audioManager;
    private AudioDeviceInfo preferredInputDevice;
    private boolean externalInputSelected;
    private AudioFocusRequest audioFocusRequest;
    private final AudioManager.OnAudioFocusChangeListener audioFocusListener = focusChange -> { };
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeakerphoneOn;
    private boolean joined;
    private boolean joining;
    private boolean muted;
    private boolean deafened;
    private VoiceActivityDetector localVoiceDetector;
    private WeakReference<Activity> pendingActivity = new WeakReference<>(null);

    static void join(Activity activity) {
        if (INSTANCE.joined || INSTANCE.joining) {
            BattlyWorldsRealtimeClient.ensureAuthenticated();
            return;
        }
        INSTANCE.loadPersistentState(activity);
        INSTANCE.pendingActivity = new WeakReference<>(activity);
        INSTANCE.joining = true;
        BattlyWorldsRealtimeClient.addListener(INSTANCE);
        notifyChanged();
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestMicrophonePermission(activity);
            }
            return;
        }
        BattlyWorldsRealtimeClient.ensureAuthenticated();
        INSTANCE.continuePendingJoin();
    }

    private static void requestMicrophonePermission(Activity activity) {
        if (BattlyWorldsPreferences.wasMicrophoneExplanationShown(activity)) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO},
                    MICROPHONE_PERMISSION_REQUEST);
            return;
        }
        androidx.appcompat.app.AlertDialog dialog = Tools.createStyledDialogBuilder(activity)
                .setTitle(R.string.battlyworlds_microphone_title)
                .setMessage(R.string.battlyworlds_microphone_explanation)
                .setNegativeButton(R.string.battlyworlds_microphone_not_now, (d, which) -> {
                    INSTANCE.cancelPendingJoin();
                })
                .setPositiveButton(R.string.battlyworlds_microphone_continue, (d, which) -> {
                    BattlyWorldsPreferences.markMicrophoneExplanationShown(activity);
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO},
                            MICROPHONE_PERMISSION_REQUEST);
                })
                .create();
        Tools.styleDialog(dialog);
        dialog.show();
    }

    static void autoJoin(Activity activity) {
        if (activity == null || activity.isFinishing()
                || !BattlyWorldsPreferences.shouldAutoJoinVoice(activity)) return;
        join(activity);
    }

    static void onMicrophonePermissionResult(Activity activity, boolean granted) {
        if (granted) {
            INSTANCE.pendingActivity = new WeakReference<>(activity);
            BattlyWorldsRealtimeClient.ensureAuthenticated();
            INSTANCE.continuePendingJoin();
        } else {
            INSTANCE.cancelPendingJoin();
            emitError(activity.getString(net.kdt.pojavlaunch.R.string.battlyworlds_voice_permission));
        }
    }

    static void leave() {
        runVoice(INSTANCE::leaveInternal);
    }

    static void setMuted(boolean value) {
        INSTANCE.muted = value;
        if (INSTANCE.appContext != null) {
            BattlyWorldsPreferences.setVoiceMuted(INSTANCE.appContext, value);
        }
        runVoice(() -> {
            synchronized (INSTANCE) {
                if (INSTANCE.audioDeviceModule != null) {
                    INSTANCE.audioDeviceModule.setMicrophoneMute(value);
                }
                if (INSTANCE.localTrack != null) INSTANCE.localTrack.setEnabled(!value);
            }
        });
        if (value) INSTANCE.setSpeakingState(BattlyWorldsRealtimeClient.getCurrentUserId(), false);
        BattlyWorldsRealtimeClient.setVoiceState(value, INSTANCE.joined);
        notifyChanged();
    }

    static void setDeafened(boolean value) {
        INSTANCE.deafened = value;
        if (INSTANCE.appContext != null) {
            BattlyWorldsPreferences.setVoiceDeafened(INSTANCE.appContext, value);
        }
        runVoice(() -> {
            synchronized (INSTANCE) {
                if (INSTANCE.audioDeviceModule != null) {
                    INSTANCE.audioDeviceModule.setSpeakerMute(value);
                }
                if (value) INSTANCE.stopRemoteSpeakingIndicators();
                INSTANCE.applyRemoteTrackStates();
            }
        });
        notifyChanged();
    }

    static void setUserSilenced(String userId, boolean value) {
        if (value) INSTANCE.locallySilenced.add(userId);
        else INSTANCE.locallySilenced.remove(userId);
        if (value) INSTANCE.setSpeakingState(userId, false);
        runVoice(() -> {
            synchronized (INSTANCE) {
                INSTANCE.applyRemoteTrackState(userId);
            }
        });
        notifyChanged();
    }

    static void setUserVolume(String userId, int volume) {
        int safeVolume = BattlyWorldsPreferences.clampVoiceUserVolume(volume);
        INSTANCE.userVolumes.put(userId, safeVolume);
        if (INSTANCE.appContext != null) {
            BattlyWorldsPreferences.setVoiceUserVolume(INSTANCE.appContext, userId, safeVolume);
        }
        runVoice(() -> {
            synchronized (INSTANCE) {
                INSTANCE.applyRemoteTrackState(userId);
            }
        });
    }

    static int getUserVolume(String userId) {
        Integer cached = INSTANCE.userVolumes.get(userId);
        if (cached != null) return cached;
        if (INSTANCE.appContext == null) return 100;
        int volume = BattlyWorldsPreferences.getVoiceUserVolume(INSTANCE.appContext, userId);
        INSTANCE.userVolumes.put(userId, volume);
        return volume;
    }

    static boolean isJoined() { return INSTANCE.joined; }
    static boolean isJoining() { return INSTANCE.joining; }
    static boolean isMuted() { return INSTANCE.muted; }
    static boolean isDeafened() { return INSTANCE.deafened; }
    static boolean isUserSilenced(String userId) { return INSTANCE.locallySilenced.contains(userId); }
    static boolean isUserSpeaking(String userId) {
        synchronized (INSTANCE) {
            return INSTANCE.speakingUsers.contains(userId);
        }
    }

    static void addListener(Listener listener) {
        if (!LISTENERS.contains(listener)) LISTENERS.add(listener);
        listener.onVoiceChanged();
    }

    static void removeListener(Listener listener) { LISTENERS.remove(listener); }

    private synchronized boolean joinInternal(Activity activity) {
        if (joined) {
            joining = false;
            return true;
        }
        if (!BattlyWorldsRealtimeClient.isAuthenticated()) return false;
        try {
            configureAudioSession(activity);
            String ownId = BattlyWorldsRealtimeClient.getCurrentUserId();
            usernames.put(ownId, BattlyWorldsInvites.getBattlyUsername(activity));
            localVoiceDetector = new VoiceActivityDetector(ownId);
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(
                    activity.getApplicationContext()).createInitializationOptions());
            BattlyWorldsVoiceAudioPolicy audioPolicy =
                    BattlyWorldsVoiceAudioPolicy.forInput(externalInputSelected);
            audioDeviceModule = JavaAudioDeviceModule.builder(activity.getApplicationContext())
                    .setAudioSource(audioPolicy.audioSource)
                    .setUseHardwareAcousticEchoCanceler(audioPolicy.hardwareEchoCanceler)
                    .setUseHardwareNoiseSuppressor(audioPolicy.hardwareNoiseSuppressor)
                    .setSamplesReadyCallback(samples -> onLocalAudioSamples(samples.getData()))
                    .createAudioDeviceModule();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferredInputDevice != null) {
                audioDeviceModule.setPreferredInputDevice(preferredInputDevice);
            }
            Log.i(TAG, "Microphone route: device=" + describeAudioDeviceCompat(preferredInputDevice)
                    + ", source=" + audioPolicy.audioSource
                    + ", hardwareAEC=" + audioPolicy.hardwareEchoCanceler
                    + ", hardwareNS=" + audioPolicy.hardwareNoiseSuppressor);
            audioDeviceModule.setMicrophoneMute(muted);
            audioDeviceModule.setSpeakerMute(deafened);
            factory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(audioDeviceModule)
                    .createPeerConnectionFactory();
            MediaConstraints constraints = new MediaConstraints();
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
            audioSource = factory.createAudioSource(constraints);
            localTrack = factory.createAudioTrack("battlyworlds-audio", audioSource);
            localTrack.setEnabled(!muted);
            joined = true;
            joining = false;
            pendingActivity.clear();
            BattlyWorldsRealtimeClient.addListener(this);
            BattlyWorldsRealtimeClient.setVoiceState(muted, true);
            onMembersChanged(BattlyWorldsRealtimeClient.getMembers());
            drainPendingVoiceSignals();
            BattlyWorldsVoiceSounds.prepare(appContext);
            BattlyWorldsVoiceSounds.playConnected(appContext);
            notifyChanged();
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Unable to start voice chat", error);
            leaveInternal();
            emitError(error.getMessage() == null ? "Voice chat error" : error.getMessage());
            return false;
        }
    }

    private synchronized void leaveInternal() {
        boolean wasJoined = joined;
        Context soundContext = appContext;
        joined = false;
        joining = false;
        pendingActivity.clear();
        BattlyWorldsRealtimeClient.removeListener(this);
        for (PeerConnection peer : peers.values()) peer.dispose();
        peers.clear();
        clearRemoteTracks();
        pendingIceCandidates.clear();
        remoteDescriptionUsers.clear();
        pendingVoiceSignals.clear();
        cancelPeerRecoveries();
        locallySilenced.clear();
        voiceDetectors.clear();
        speakingUsers.clear();
        connectedVoiceUsers.clear();
        usernames.clear();
        localVoiceDetector = null;
        if (localTrack != null) localTrack.dispose();
        if (audioSource != null) audioSource.dispose();
        if (factory != null) factory.dispose();
        if (audioDeviceModule != null) {
            audioDeviceModule.release();
        }
        localTrack = null;
        audioSource = null;
        factory = null;
        audioDeviceModule = null;
        preferredInputDevice = null;
        externalInputSelected = false;
        releaseAudioSession();
        BattlyWorldsVoiceOverlay.clear(BattlyWorldsManager.getAttachedActivity());
        if (wasJoined) BattlyWorldsRealtimeClient.setVoiceState(muted, false);
        if (wasJoined) BattlyWorldsVoiceSounds.playDisconnected(soundContext);
        notifyChanged();
    }

    private synchronized void continuePendingJoin() {
        if (!joining || joined) return;
        Activity activity = pendingActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            cancelPendingJoin();
            return;
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return;
        if (!BattlyWorldsRealtimeClient.isAuthenticated()) {
            notifyChanged();
            return;
        }
        joinInternal(activity);
    }

    private synchronized void cancelPendingJoin() {
        joining = false;
        pendingActivity.clear();
        if (!joined) BattlyWorldsRealtimeClient.removeListener(this);
        notifyChanged();
    }

    @Override
    public synchronized void onConnectionStateChanged(boolean authenticated, String error) {
        if (authenticated) {
            if (joined) {
                BattlyWorldsRealtimeClient.setVoiceState(muted, true);
                onMembersChanged(BattlyWorldsRealtimeClient.getMembers());
            }
            continuePendingJoin();
            return;
        }
        if (joined) resetRemotePeers();
        if (!joining || error.isEmpty() || "connection_failed".equals(error)
                || "disconnected".equals(error)) return;
        Activity activity = pendingActivity.get();
        cancelPendingJoin();
        if (activity != null) {
            emitError(activity.getString(net.kdt.pojavlaunch.R.string.battlyworlds_voice_connection_failed));
        }
    }

    private void resetRemotePeers() {
        cancelPeerRecoveries();
        for (PeerConnection peer : peers.values()) peer.dispose();
        peers.clear();
        clearRemoteTracks();
        pendingIceCandidates.clear();
        remoteDescriptionUsers.clear();
        notifyChanged();
    }

    @Override
    public synchronized void onMembersChanged(List<BattlyWorldsRealtimeClient.Member> members) {
        if (!joined) return;
        String ownId = BattlyWorldsRealtimeClient.getCurrentUserId();
        String ownChannel = BattlyWorldsRealtimeClient.getCurrentVoiceChannel();
        Set<String> active = new HashSet<>();
        for (BattlyWorldsRealtimeClient.Member member : members) {
            usernames.put(member.userId, member.username);
            if (!member.userId.equals(ownId) && member.voiceConnected
                    && ownChannel.equals(member.voiceChannel)) {
                active.add(member.userId);
                if (connectedVoiceUsers.add(member.userId)) {
                    BattlyWorldsVoiceSounds.playConnected(appContext);
                }
                ensurePeerNegotiation(member.userId);
            }
        }
        for (String previous : new HashSet<>(connectedVoiceUsers)) {
            if (!active.contains(previous)) {
                connectedVoiceUsers.remove(previous);
                BattlyWorldsVoiceSounds.playDisconnected(appContext);
            }
        }
        new ArrayList<>(peers.keySet()).stream().filter(id -> !active.contains(id)).forEach(this::removePeer);
    }

    @Override
    public synchronized void onVoiceState(String userId, boolean remoteMuted, boolean connected) {
        if (!joined || userId.equals(BattlyWorldsRealtimeClient.getCurrentUserId())) return;
        if (connected) {
            if (connectedVoiceUsers.add(userId)) {
                BattlyWorldsVoiceSounds.playConnected(appContext);
            }
            ensurePeerNegotiation(userId);
        } else {
            if (connectedVoiceUsers.remove(userId)) {
                BattlyWorldsVoiceSounds.playDisconnected(appContext);
            }
            removePeer(userId);
        }
        onMembersChanged(BattlyWorldsRealtimeClient.getMembers());
    }

    private void ensurePeerNegotiation(String userId) {
        if (userId == null || userId.isEmpty() || peers.containsKey(userId)) return;
        String ownId = BattlyWorldsRealtimeClient.getCurrentUserId();
        if (ownId.isEmpty() || ownId.equals(userId)) return;
        if (ownId.compareTo(userId) < 0) {
            createOffer(userId);
        } else {
            // The deterministic initiator normally offers first. If its first room-state
            // event was missed, recover without requiring either player to reconnect.
            schedulePeerRecovery(userId, 3_500L);
        }
    }

    @Override
    public synchronized void onVoiceChannelChanged(String voiceChannel) {
        if (!joined) return;
        resetRemotePeers();
        BattlyWorldsRealtimeClient.setVoiceState(muted, true);
        onMembersChanged(BattlyWorldsRealtimeClient.getMembers());
    }

    @Override
    public synchronized void onVoiceSignal(String from, JSONObject signal) {
        if (from.isEmpty()) return;
        if (!joined) {
            if (joining && pendingVoiceSignals.size() < 64) {
                pendingVoiceSignals.add(new PendingVoiceSignal(from, signal));
            }
            return;
        }
        JSONObject signalCopy;
        try {
            signalCopy = new JSONObject(signal.toString());
        } catch (Exception exception) {
            return;
        }
        runVoice(() -> {
            synchronized (BattlyWorldsVoiceManager.this) {
                if (joined) handleVoiceSignal(from, signalCopy);
            }
        });
    }

    private void handleVoiceSignal(String from, JSONObject signal) {
        String type = signal.optString("type", "");
        if ("offer".equals(type)) {
            PeerConnection peer = peer(from);
            peer.setRemoteDescription(new SimpleSdpObserver(() -> {
                        remoteDescriptionUsers.add(from);
                        flushPendingIceCandidates(from);
                        createAnswer(from);
                    }),
                    new SessionDescription(SessionDescription.Type.OFFER, signal.optString("sdp", "")));
        } else if ("answer".equals(type)) {
            PeerConnection peer = peers.get(from);
            if (peer != null) peer.setRemoteDescription(new SimpleSdpObserver(() -> {
                            remoteDescriptionUsers.add(from);
                            flushPendingIceCandidates(from);
                        }),
                    new SessionDescription(SessionDescription.Type.ANSWER, signal.optString("sdp", "")));
        } else if ("candidate".equals(type)) {
            PeerConnection peer = peer(from);
            IceCandidate candidate = new IceCandidate(signal.optString("sdpMid", "audio"),
                    signal.optInt("sdpMLineIndex", 0), signal.optString("candidate", ""));
            if (!remoteDescriptionUsers.contains(from)) {
                pendingIceCandidates.computeIfAbsent(from, ignored -> new ArrayList<>()).add(candidate);
            } else {
                peer.addIceCandidate(candidate);
            }
        } else if ("restart".equals(type)) {
            removePeer(from);
            String ownId = BattlyWorldsRealtimeClient.getCurrentUserId();
            if (joined && ownId.compareTo(from) < 0) createOffer(from);
        }
    }

    private void createOffer(String userId) {
        PeerConnection peer = peer(userId);
        peer.createOffer(new DescriptionObserver(userId, "offer"), receiveAudioConstraints());
        schedulePeerRecovery(userId, 6500L);
    }

    private void drainPendingVoiceSignals() {
        if (!joined || pendingVoiceSignals.isEmpty()) return;
        List<PendingVoiceSignal> queued = new ArrayList<>(pendingVoiceSignals);
        pendingVoiceSignals.clear();
        for (PendingVoiceSignal pending : queued) {
            handleVoiceSignal(pending.from, pending.signal);
        }
    }

    private void createAnswer(String userId) {
        PeerConnection peer = peer(userId);
        peer.createAnswer(new DescriptionObserver(userId, "answer"), receiveAudioConstraints());
    }

    private PeerConnection peer(String userId) {
        PeerConnection existing = peers.get(userId);
        if (existing != null) return existing;
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        for (BattlyWorldsRealtimeClient.IceServerConfig server
                : BattlyWorldsRealtimeClient.getIceServers()) {
            PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(server.url);
            if (!server.username.isEmpty()) builder.setUsername(server.username);
            if (!server.credential.isEmpty()) builder.setPassword(server.credential);
            iceServers.add(builder.createIceServer());
        }
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        PeerConnection created = factory.createPeerConnection(config, new PeerObserver(userId));
        if (created == null) throw new IllegalStateException("Unable to create voice peer");
        created.addTrack(localTrack, Collections.singletonList("battlyworlds"));
        peers.put(userId, created);
        return created;
    }

    private void removePeer(String userId) {
        cancelPeerRecovery(userId);
        PeerConnection peer = peers.remove(userId);
        if (peer != null) peer.dispose();
        AudioTrack track = remoteTracks.remove(userId);
        AudioTrackSink sink = remoteTrackSinks.remove(userId);
        if (track != null && sink != null) track.removeSink(sink);
        voiceDetectors.remove(userId);
        setSpeakingState(userId, false);
        usernames.remove(userId);
        pendingIceCandidates.remove(userId);
        remoteDescriptionUsers.remove(userId);
    }

    private void clearRemoteTracks() {
        for (Map.Entry<String, AudioTrack> entry : remoteTracks.entrySet()) {
            AudioTrackSink sink = remoteTrackSinks.get(entry.getKey());
            if (sink != null) entry.getValue().removeSink(sink);
            setSpeakingState(entry.getKey(), false);
        }
        remoteTracks.clear();
        remoteTrackSinks.clear();
        voiceDetectors.clear();
    }

    private void flushPendingIceCandidates(String userId) {
        PeerConnection peer = peers.get(userId);
        List<IceCandidate> candidates = pendingIceCandidates.remove(userId);
        if (peer == null || candidates == null) return;
        for (IceCandidate candidate : candidates) peer.addIceCandidate(candidate);
    }

    private void applyRemoteTrackStates() {
        for (String userId : remoteTracks.keySet()) applyRemoteTrackState(userId);
    }

    private void applyRemoteTrackState(String userId) {
        AudioTrack track = remoteTracks.get(userId);
        if (track != null) {
            boolean audible = !deafened && !locallySilenced.contains(userId);
            track.setEnabled(audible);
            track.setVolume(audible ? getUserVolume(userId) / 100.0d : 0.0d);
        }
    }

    private static MediaConstraints receiveAudioConstraints() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        return constraints;
    }

    private static void sendDescription(String userId, String type, SessionDescription description) {
        JSONObject signal = new JSONObject();
        try {
            signal.put("type", type);
            signal.put("sdp", description.description);
            BattlyWorldsRealtimeClient.sendSignal(userId, signal);
        } catch (Exception ignored) { }
    }

    private static void notifyChanged() {
        BattlyWorldsVoiceNotification.update(INSTANCE.appContext, INSTANCE.joined,
                INSTANCE.joining, INSTANCE.muted, INSTANCE.deafened);
        for (Listener listener : LISTENERS) listener.onVoiceChanged();
    }

    private static void runVoice(Runnable operation) {
        VOICE_EXECUTOR.execute(() -> {
            try {
                operation.run();
            } catch (Throwable error) {
                Log.e(TAG, "Voice operation failed", error);
            }
        });
    }

    private static void emitError(String message) {
        for (Listener listener : LISTENERS) listener.onVoiceError(message);
    }

    private final class PeerObserver implements PeerConnection.Observer {
        private final String userId;
        PeerObserver(String userId) { this.userId = userId; }
        @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
            Log.i(TAG, "ICE " + userId + ": " + state);
            if (state == PeerConnection.IceConnectionState.CONNECTED
                    || state == PeerConnection.IceConnectionState.COMPLETED) {
                synchronized (BattlyWorldsVoiceManager.this) {
                    cancelPeerRecovery(userId);
                }
            } else if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                schedulePeerRecovery(userId, 3500L);
            } else if (state == PeerConnection.IceConnectionState.FAILED) {
                schedulePeerRecovery(userId, 900L);
                Context context = appContext;
                if (context != null) {
                    emitError(context.getString(
                            net.kdt.pojavlaunch.R.string.battlyworlds_voice_connection_failed));
                }
            }
        }
        @Override public void onIceConnectionReceivingChange(boolean receiving) { }
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
        @Override public void onIceCandidate(IceCandidate candidate) {
            JSONObject signal = new JSONObject();
            try {
                signal.put("type", "candidate");
                signal.put("sdpMid", candidate.sdpMid);
                signal.put("sdpMLineIndex", candidate.sdpMLineIndex);
                signal.put("candidate", candidate.sdp);
                BattlyWorldsRealtimeClient.sendSignal(userId, signal);
            } catch (Exception ignored) { }
        }
        @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
        @Override public void onAddStream(MediaStream stream) { }
        @Override public void onRemoveStream(MediaStream stream) { }
        @Override public void onDataChannel(DataChannel channel) { }
        @Override public void onRenegotiationNeeded() { }
        @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
            if (receiver.track() instanceof AudioTrack) {
                AudioTrack remoteTrack = (AudioTrack) receiver.track();
                runVoice(() -> {
                    synchronized (BattlyWorldsVoiceManager.this) {
                        if (!joined || !peers.containsKey(userId)) return;
                        remoteTrack.setVolume(1.0d);
                        AudioTrackSink oldSink = remoteTrackSinks.remove(userId);
                        AudioTrack oldTrack = remoteTracks.get(userId);
                        if (oldTrack != null && oldTrack != remoteTrack) oldTrack.setEnabled(false);
                        if (oldTrack != null && oldSink != null) oldTrack.removeSink(oldSink);
                        AudioTrackSink sink = new SpeakingSink(userId);
                        remoteTrack.addSink(sink);
                        remoteTrackSinks.put(userId, sink);
                        remoteTracks.put(userId, remoteTrack);
                        applyRemoteTrackState(userId);
                        Log.i(TAG, "Remote audio track received from " + userId);
                        notifyChanged();
                    }
                });
            }
        }
    }

    private synchronized void loadPersistentState(Context context) {
        appContext = context.getApplicationContext();
        muted = BattlyWorldsPreferences.isVoiceMuted(appContext);
        deafened = BattlyWorldsPreferences.isVoiceDeafened(appContext);
    }

    private void onLocalAudioSamples(byte[] data) {
        VoiceActivityDetector detector;
        synchronized (this) {
            detector = localVoiceDetector;
        }
        if (detector != null && !muted) detector.process(data);
    }

    private synchronized void stopRemoteSpeakingIndicators() {
        String ownId = BattlyWorldsRealtimeClient.getCurrentUserId();
        for (String userId : new HashSet<>(speakingUsers)) {
            if (!userId.equals(ownId)) setSpeakingState(userId, false);
        }
    }

    private synchronized void setSpeakingState(String userId, boolean speaking) {
        if (userId == null || userId.isEmpty()) return;
        boolean changed = speaking ? speakingUsers.add(userId) : speakingUsers.remove(userId);
        if (!changed) return;
        String username = usernames.get(userId);
        if (username == null || username.isEmpty()) username = userId;
        BattlyWorldsVoiceOverlay.setSpeaking(BattlyWorldsManager.getAttachedActivity(),
                userId, username, speaking);
        notifyChanged();
    }

    private final class SpeakingSink implements AudioTrackSink {
        private final VoiceActivityDetector detector;

        SpeakingSink(String userId) {
            detector = voiceDetectors.computeIfAbsent(userId, VoiceActivityDetector::new);
        }

        @Override
        public void onData(ByteBuffer data, int bitsPerSample, int sampleRate,
                           int channels, int frames, long absoluteCaptureTimestampMs) {
            detector.process(data, bitsPerSample, channels, frames);
        }
    }

    private final class VoiceActivityDetector {
        private static final double SPEAKING_RMS = 520.0d;
        private static final long RELEASE_MS = 380L;
        private final String userId;
        private long lastLoudAt;
        private int loudFrames;
        private boolean speaking;

        VoiceActivityDetector(String userId) {
            this.userId = userId;
        }

        void process(byte[] data) {
            if (data == null || data.length < 2) return;
            double sum = 0d;
            int samples = 0;
            for (int i = 0; i + 1 < data.length; i += 4) {
                short value = (short) ((data[i] & 0xff) | (data[i + 1] << 8));
                sum += (double) value * value;
                samples++;
            }
            update(samples == 0 ? 0d : Math.sqrt(sum / samples));
        }

        void process(ByteBuffer data, int bitsPerSample, int channels, int frames) {
            if (data == null || bitsPerSample != 16) return;
            ByteBuffer buffer = data.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            int requested = Math.max(0, frames * Math.max(1, channels));
            int available = Math.min(requested, buffer.remaining() / 2);
            double sum = 0d;
            int samples = 0;
            for (int i = 0; i < available; i += 2) {
                short value = buffer.getShort(buffer.position() + i * 2);
                sum += (double) value * value;
                samples++;
            }
            update(samples == 0 ? 0d : Math.sqrt(sum / samples));
        }

        private void update(double rms) {
            long now = SystemClock.elapsedRealtime();
            if (rms >= SPEAKING_RMS) {
                lastLoudAt = now;
                loudFrames++;
                if (!speaking && loudFrames >= 2) {
                    speaking = true;
                    setSpeakingState(userId, true);
                }
            } else {
                loudFrames = 0;
                if (speaking && now - lastLoudAt >= RELEASE_MS) {
                    speaking = false;
                    setSpeakingState(userId, false);
                }
            }
        }
    }

    private void configureAudioSession(Activity activity) {
        appContext = activity.getApplicationContext();
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;
        previousAudioMode = audioManager.getMode();
        previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        activity.setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }

        AudioDeviceInfo selectedOutput = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                int type = device.getType();
                if (isExternalAudioType(type)) {
                    selectedOutput = device;
                    break;
                }
                if (selectedOutput == null && type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    selectedOutput = device;
                }
            }
            if (selectedOutput != null) audioManager.setCommunicationDevice(selectedOutput);
        } else {
            audioManager.setSpeakerphoneOn(true);
        }

        preferredInputDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? chooseInputDevice(audioManager, selectedOutput)
                : null;
        externalInputSelected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && preferredInputDevice != null
                && isExternalAudioType(preferredInputDevice.getType());
        Log.i(TAG, "Android voice audio session configured: output="
                + describeAudioDeviceCompat(selectedOutput) + ", input="
                + describeAudioDeviceCompat(preferredInputDevice));
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static AudioDeviceInfo chooseInputDevice(AudioManager manager, AudioDeviceInfo selectedOutput) {
        AudioDeviceInfo builtIn = null;
        AudioDeviceInfo external = null;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC && builtIn == null) {
                builtIn = device;
            } else if (isExternalAudioType(device.getType()) && external == null) {
                external = device;
            }
        }
        if (selectedOutput != null && isExternalAudioType(selectedOutput.getType()) && external != null) {
            return external;
        }
        return builtIn != null ? builtIn : external;
    }

    private static boolean isExternalAudioType(int type) {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_DEVICE;
    }

    private static String describeAudioDeviceCompat(AudioDeviceInfo device) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "default";
        return describeAudioDevice(device);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static String describeAudioDevice(AudioDeviceInfo device) {
        if (device == null) return "default";
        CharSequence name = device.getProductName();
        return device.getType() + ":" + (name == null ? "unknown" : name);
    }

    private void releaseAudioSession() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice();
        } else {
            audioManager.setSpeakerphoneOn(previousSpeakerphoneOn);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(audioFocusListener);
        }
        audioManager.setMode(previousAudioMode);
        audioFocusRequest = null;
        audioManager = null;
    }

    private synchronized void schedulePeerRecovery(String userId, long delayMs) {
        if (!joined || userId == null || userId.isEmpty() || peerRecoveries.containsKey(userId)) return;
        Runnable recovery = () -> runVoice(() -> {
            synchronized (BattlyWorldsVoiceManager.this) {
                peerRecoveries.remove(userId);
                if (!joined || !isActiveVoiceMember(userId)) return;
                Log.i(TAG, "Rebuilding stalled voice peer " + userId);
                removePeer(userId);
                sendRestartSignal(userId);
                createOffer(userId);
            }
        });
        peerRecoveries.put(userId, recovery);
        recoveryHandler.postDelayed(recovery, delayMs);
    }

    private boolean isActiveVoiceMember(String userId) {
        String channel = BattlyWorldsRealtimeClient.getCurrentVoiceChannel();
        for (BattlyWorldsRealtimeClient.Member member : BattlyWorldsRealtimeClient.getMembers()) {
            if (userId.equals(member.userId) && member.voiceConnected
                    && channel.equals(member.voiceChannel)) return true;
        }
        return false;
    }

    private void sendRestartSignal(String userId) {
        JSONObject signal = new JSONObject();
        try {
            signal.put("type", "restart");
            BattlyWorldsRealtimeClient.sendSignal(userId, signal);
        } catch (Exception ignored) { }
    }

    private void cancelPeerRecovery(String userId) {
        Runnable recovery = peerRecoveries.remove(userId);
        if (recovery != null) recoveryHandler.removeCallbacks(recovery);
    }

    private void cancelPeerRecoveries() {
        for (Runnable recovery : peerRecoveries.values()) recoveryHandler.removeCallbacks(recovery);
        peerRecoveries.clear();
    }

    private static final class SimpleSdpObserver implements SdpObserver {
        private final Runnable success;
        SimpleSdpObserver(Runnable success) { this.success = success; }
        @Override public void onCreateSuccess(SessionDescription description) { }
        @Override public void onSetSuccess() {
            if (success != null) runVoice(success);
        }
        @Override public void onCreateFailure(String error) { Log.w(TAG, error); }
        @Override public void onSetFailure(String error) { Log.w(TAG, error); }
    }

    private static final class PendingVoiceSignal {
        final String from;
        final JSONObject signal;

        PendingVoiceSignal(String from, JSONObject signal) {
            this.from = from;
            this.signal = signal;
        }
    }

    private final class DescriptionObserver implements SdpObserver {
        private final String userId;
        private final String type;
        DescriptionObserver(String userId, String type) { this.userId = userId; this.type = type; }
        @Override public void onCreateSuccess(SessionDescription description) {
            runVoice(() -> {
                synchronized (BattlyWorldsVoiceManager.this) {
                    PeerConnection peer = peers.get(userId);
                    if (peer != null) peer.setLocalDescription(new SimpleSdpObserver(
                            () -> sendDescription(userId, type, description)), description);
                }
            });
        }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String error) { Log.w(TAG, error); }
        @Override public void onSetFailure(String error) { Log.w(TAG, error); }
    }

    private BattlyWorldsVoiceManager() { }
}
