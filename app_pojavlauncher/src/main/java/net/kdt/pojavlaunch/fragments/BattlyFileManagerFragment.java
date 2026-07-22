package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BattlyFileManagerFragment extends Fragment {
    public static final String TAG = "BattlyFileManagerFragment";
    private static final String ARG_PATH = "path";
    private static final int MAX_TEXT_PREVIEW = 256 * 1024;

    private final List<File> entries = new ArrayList<>();
    private final FileAdapter adapter = new FileAdapter();
    private File storageRoot;
    private File currentDirectory;
    private TextView pathView;
    private TextView emptyView;
    private ImageButton upButton;
    private ImageButton viewModeButton;
    private RecyclerView fileList;
    private boolean gridMode;
    private int loadGeneration;

    public BattlyFileManagerFragment() {
        super(R.layout.fragment_battly_file_manager);
    }

    public static Bundle createArguments(File path) {
        Bundle args = new Bundle();
        if (path != null) args.putString(ARG_PATH, path.getAbsolutePath());
        return args;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        storageRoot = canonical(new File(Tools.DIR_GAME_HOME));
        Bundle arguments = getArguments();
        String requestedPath = arguments == null
                ? storageRoot.getAbsolutePath()
                : arguments.getString(ARG_PATH, storageRoot.getAbsolutePath());
        File requested = canonical(new File(requestedPath));
        currentDirectory = isInsideStorage(requested) && requested.isDirectory() ? requested : storageRoot;

        pathView = view.findViewById(R.id.battly_files_path);
        emptyView = view.findViewById(R.id.battly_files_empty);
        upButton = view.findViewById(R.id.battly_files_up);
        fileList = view.findViewById(R.id.battly_files_list);
        viewModeButton = view.findViewById(R.id.battly_files_view_mode);
        applyViewMode(false);

        upButton.setOnClickListener(v -> navigateUp());
        view.findViewById(R.id.battly_files_home).setOnClickListener(v -> openDirectory(storageRoot));
        view.findViewById(R.id.battly_files_refresh).setOnClickListener(v -> loadDirectory());
        viewModeButton.setOnClickListener(v -> applyViewMode(!gridMode));
        view.findViewById(R.id.battly_files_open_system).setOnClickListener(v -> openInSystemExplorer());
        loadDirectory();
    }

    private void applyViewMode(boolean useGrid) {
        gridMode = useGrid;
        fileList.setAdapter(null);
        fileList.setLayoutManager(useGrid
                ? new GridLayoutManager(requireContext(), 3)
                : new LinearLayoutManager(requireContext()));
        fileList.setAdapter(adapter);
        viewModeButton.setImageResource(useGrid ? R.drawable.ic_view_list : R.drawable.ic_view_grid);
        viewModeButton.setContentDescription(getString(useGrid
                ? R.string.battly_files_list_view : R.string.battly_files_grid_view));
    }

    private void openInSystemExplorer() {
        try {
            Tools.openPath(requireContext(), currentDirectory, false);
            return;
        } catch (Throwable ignored) {
            // Fall back to the Storage Access Framework on devices without a directory viewer.
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            File externalFiles = requireContext().getExternalFilesDir(null);
            String absolutePath = currentDirectory.getAbsolutePath().replace(File.separatorChar, '/');
            String documentPath = null;
            if (externalFiles != null) {
                String externalPath = externalFiles.getAbsolutePath().replace(File.separatorChar, '/');
                int androidIndex = externalPath.indexOf("/Android/");
                if (androidIndex >= 0 && absolutePath.startsWith(externalPath)) {
                    String rootRelative = externalPath.substring(androidIndex + 1);
                    String childRelative = absolutePath.substring(externalPath.length());
                    documentPath = rootRelative + childRelative;
                }
            }
            if (documentPath != null) {
                Uri initialUri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents", "primary:" + documentPath);
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
            }
        }
        try {
            startActivity(intent);
        } catch (Throwable throwable) {
            Toast.makeText(requireContext(), R.string.battly_files_open_system_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateUp() {
        if (sameFile(currentDirectory, storageRoot)) {
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }
        File parent = canonical(currentDirectory.getParentFile());
        openDirectory(isInsideStorage(parent) ? parent : storageRoot);
    }

    private void openDirectory(File directory) {
        File safe = canonical(directory);
        if (!isInsideStorage(safe) || !safe.isDirectory()) return;
        currentDirectory = safe;
        loadDirectory();
    }

    private void loadDirectory() {
        final int generation = ++loadGeneration;
        pathView.setText(relativePath(currentDirectory));
        upButton.setAlpha(sameFile(currentDirectory, storageRoot) ? 0.7f : 1f);
        File directory = currentDirectory;
        PojavApplication.sExecutorService.execute(() -> {
            File[] files = directory.listFiles(file -> file != null && !file.getName().equals("."));
            List<File> loaded = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
            loaded.sort(Comparator.comparing((File file) -> !file.isDirectory())
                    .thenComparing(file -> file.getName().toLowerCase(Locale.ROOT)));
            Tools.runOnUiThread(() -> {
                if (!isAdded() || generation != loadGeneration) return;
                entries.clear();
                entries.addAll(loaded);
                adapter.notifyDataSetChanged();
                emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void openFile(File file) {
        String extension = extension(file.getName());
        if (isTextExtension(extension)) {
            showTextPreview(file);
        } else if (isImageExtension(extension)) {
            showImagePreview(file);
        } else {
            Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                    .setTitle(file.getName())
                    .setMessage(getString(R.string.battly_files_no_preview) + "\n\n"
                            + formatSize(file.length()) + "\n" + relativePath(file))
                    .setPositiveButton(android.R.string.ok, null));
        }
    }

    private void showTextPreview(File file) {
        PojavApplication.sExecutorService.execute(() -> {
            try (FileInputStream input = new FileInputStream(file)) {
                int length = (int) Math.min(file.length(), MAX_TEXT_PREVIEW);
                byte[] bytes = new byte[length];
                int read = input.read(bytes);
                String text = new String(bytes, 0, Math.max(read, 0), StandardCharsets.UTF_8);
                if (file.length() > MAX_TEXT_PREVIEW) {
                    text += "\n\n" + getString(R.string.battly_files_preview_too_large);
                }
                String content = text;
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    TextView preview = new TextView(requireContext());
                    preview.setText(content);
                    preview.setTextColor(0xFFE7F0F5);
                    preview.setTextSize(12);
                    preview.setTextIsSelectable(true);
                    preview.setPadding(dp(16), dp(12), dp(16), dp(12));
                    ScrollView scroll = new ScrollView(requireContext());
                    scroll.addView(preview);
                    Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                            .setTitle(file.getName())
                            .setView(scroll)
                            .setPositiveButton(android.R.string.ok, null));
                });
            } catch (Throwable throwable) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) Tools.showError(requireContext(), throwable);
                });
            }
        });
    }

    private void showImagePreview(File file) {
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            Tools.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (bitmap == null) {
                    Toast.makeText(requireContext(), R.string.battly_files_no_preview, Toast.LENGTH_SHORT).show();
                    return;
                }
                ImageView preview = new ImageView(requireContext());
                preview.setAdjustViewBounds(true);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setImageBitmap(bitmap);
                preview.setPadding(dp(12), dp(8), dp(12), dp(8));
                Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                        .setTitle(file.getName())
                        .setView(preview)
                        .setPositiveButton(android.R.string.ok, null));
            });
        });
    }

    private void confirmDelete(File file) {
        if (!isInsideStorage(file) || sameFile(file, storageRoot) || !file.isFile()) return;
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.battly_files_delete_title)
                .setMessage(getString(R.string.battly_files_delete_message, file.getName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_delete, (dialog, which) ->
                        PojavApplication.sExecutorService.execute(() -> {
                            boolean deleted;
                            try {
                                if (!file.delete()) throw new IllegalStateException("delete failed");
                                deleted = true;
                            } catch (Throwable ignored) {
                                deleted = false;
                            }
                            boolean result = deleted;
                            Tools.runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), result
                                                ? R.string.battly_files_deleted
                                                : R.string.battly_files_delete_failed,
                                        Toast.LENGTH_SHORT).show();
                                if (result) loadDirectory();
                            });
                        })));
    }

    private final class FileAdapter extends RecyclerView.Adapter<FileHolder> {
        @Override
        public int getItemViewType(int position) {
            return gridMode ? 1 : 0;
        }

        @NonNull
        @Override
        public FileHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new FileHolder(LayoutInflater.from(parent.getContext())
                    .inflate(viewType == 1 ? R.layout.item_battly_file_grid : R.layout.item_battly_file,
                            parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull FileHolder holder, int position) {
            holder.bind(entries.get(position));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    private final class FileHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView name;
        private final TextView meta;
        private final ImageButton delete;

        FileHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.battly_file_icon);
            name = itemView.findViewById(R.id.battly_file_name);
            meta = itemView.findViewById(R.id.battly_file_meta);
            delete = itemView.findViewById(R.id.battly_file_delete);
        }

        void bind(File file) {
            name.setText(file.getName());
            icon.setImageResource(file.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
            if (file.isDirectory()) {
                String[] children = file.list();
                meta.setText(getString(R.string.battly_files_folder, children == null ? 0 : children.length));
            } else {
                meta.setText(getString(R.string.battly_files_file, formatSize(file.length()),
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(new Date(file.lastModified()))));
            }
            itemView.setOnClickListener(v -> {
                if (file.isDirectory()) openDirectory(file);
                else openFile(file);
            });
            delete.setOnClickListener(v -> confirmDelete(file));
            delete.setVisibility(file.isFile() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isInsideStorage(File file) {
        if (file == null) return false;
        String rootPath = storageRoot.getAbsolutePath();
        String path = canonical(file).getAbsolutePath();
        return path.equals(rootPath) || path.startsWith(rootPath + File.separator);
    }

    private String relativePath(File file) {
        String rootPath = storageRoot.getAbsolutePath();
        String path = canonical(file).getAbsolutePath();
        if (path.equals(rootPath)) return getString(R.string.battly_files_root);
        String relative = path.substring(Math.min(path.length(), rootPath.length()))
                .replace(File.separatorChar, '/');
        return getString(R.string.battly_files_root) + relative;
    }

    private static File canonical(File file) {
        if (file == null) return new File(Tools.DIR_GAME_HOME);
        try {
            return file.getCanonicalFile();
        } catch (Exception ignored) {
            return file.getAbsoluteFile();
        }
    }

    private static boolean sameFile(File first, File second) {
        return first != null && second != null
                && canonical(first).getAbsolutePath().equals(canonical(second).getAbsolutePath());
    }

    private static String extension(String name) {
        if (TextUtils.isEmpty(name)) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isTextExtension(String extension) {
        return Arrays.asList("txt", "log", "json", "properties", "cfg", "conf", "toml",
                "xml", "yml", "yaml", "md", "lang", "ini").contains(extension);
    }

    private static boolean isImageExtension(String extension) {
        return Arrays.asList("png", "jpg", "jpeg", "webp", "bmp").contains(extension);
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.1f GB", bytes / 1073741824d);
        if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / 1048576d);
        if (bytes >= 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024d);
        return bytes + " B";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
