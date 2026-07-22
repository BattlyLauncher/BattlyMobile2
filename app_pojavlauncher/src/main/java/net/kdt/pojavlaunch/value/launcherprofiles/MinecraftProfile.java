package net.kdt.pojavlaunch.value.launcherprofiles;

import androidx.annotation.Keep;

@Keep
public class MinecraftProfile {

	public static String LATEST_RELEASE = "latest-release";
	public static String LATEST_SNAPSHOT= "latest-snapshot";

	public String name;
	public String type;
	public String created;
	public String lastUsed;
	public String icon;
	public String lastVersionId;
	public String gameDir;
	public String javaDir;
	public String javaArgs;
	public String logConfig;
	public boolean logConfigIsXML;
	public String pojavRendererName;
	public String controlFile;
	public MinecraftResolution[] resolution;
	// Optional Battly instance metadata. Old launcher_profiles.json files remain valid.
	public String battlyInstanceId;
	public int battlySchemaVersion;
	public long battlyCreatedAt;
	public long battlyUpdatedAt;
	public String loaderName;
	public String loaderVersion;
	public String sourceProvider;
	public String sourceProjectId;
	public String sourceVersionId;
	public String sourceVersionName;
	public String sourceDownloadUrl;
	public String sourceHash;
	public String gamepadProfile;


	public static MinecraftProfile createTemplate(){
		MinecraftProfile TEMPLATE = new MinecraftProfile();
		TEMPLATE.name = "";
		TEMPLATE.lastVersionId = LATEST_RELEASE;
		return TEMPLATE;
	}

	public static MinecraftProfile getDefaultProfile(){
		MinecraftProfile defaultProfile = new MinecraftProfile();
		defaultProfile.name = "Default";
		defaultProfile.lastVersionId = "1.7.10";
		return defaultProfile;
	}

	public MinecraftProfile(){}

	public MinecraftProfile(MinecraftProfile profile){
		name = profile.name;
		type = profile.type;
		created = profile.created;
		lastUsed = profile.lastUsed;
		icon = profile.icon;
		lastVersionId = profile.lastVersionId;
		gameDir = profile.gameDir;
		javaDir = profile.javaDir;
		javaArgs = profile.javaArgs;
		logConfig = profile.logConfig;
		logConfigIsXML = profile.logConfigIsXML;
		pojavRendererName = profile.pojavRendererName;
		controlFile = profile.controlFile;
		resolution = profile.resolution;
		battlyInstanceId = profile.battlyInstanceId;
		battlySchemaVersion = profile.battlySchemaVersion;
		battlyCreatedAt = profile.battlyCreatedAt;
		battlyUpdatedAt = profile.battlyUpdatedAt;
		loaderName = profile.loaderName;
		loaderVersion = profile.loaderVersion;
		sourceProvider = profile.sourceProvider;
		sourceProjectId = profile.sourceProjectId;
		sourceVersionId = profile.sourceVersionId;
		sourceVersionName = profile.sourceVersionName;
		sourceDownloadUrl = profile.sourceDownloadUrl;
		sourceHash = profile.sourceHash;
		gamepadProfile = profile.gamepadProfile;
	}
}
