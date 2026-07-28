package net.kdt.pojavlaunch;

import android.os.Build;

/**
 * This class aims at providing a simple and easy way to deal with the device architecture.
 */
public class Architecture {
	public static final int UNSUPPORTED_ARCH = -1;
	public static final int ARCH_ARM64 = 0x1;
	public static final int ARCH_ARM = 0x2;
	public static final int ARCH_X86 = 0x4;
	public static final int ARCH_X86_64 = 0x8;
	private static volatile int sProcessArchitecture = UNSUPPORTED_ARCH;

	/* On both 32-bit ARM and x86, the top 1GB is reserved for kernel use. */
	public static final long ADDRESS_SPACE_LIMIT_32_BIT = 0xbfffffffL;
	/*
	 * Technically, this is supposed to be 48 bits on x86_64, but nobody's allocating
	 * 524288 terabytes of RAM on Pojav any time soon.
	 */
	public static final long ADDRESS_SPACE_LIMIT_64_BIT = 0x7fffffffffL;

	/**
	 * Get the highest byte accessible within the process's address space.
	 * @return the highest byte accessible within the process's address space.
	 */
	public static long getAddressSpaceLimit() {
		return is64BitsDevice() ? ADDRESS_SPACE_LIMIT_64_BIT : ADDRESS_SPACE_LIMIT_32_BIT;
	}

	/**
	 * Tell us if the device supports 64 bits architecture
	 * @return If the device supports 64 bits architecture
	 */
	public static boolean is64BitsDevice(){
		if (sProcessArchitecture != UNSUPPORTED_ARCH) {
			return sProcessArchitecture == ARCH_ARM64 || sProcessArchitecture == ARCH_X86_64;
		}
		return Build.SUPPORTED_64_BIT_ABIS.length != 0;
	}

	/**
	 * Tell us if the device supports 32 bits architecture
	 * Note, that a 64 bits device won't be reported as supporting 32 bits.
	 * @return If the device supports 32 bits architecture
	 */
	public static boolean is32BitsDevice(){
		return !is64BitsDevice();
	}

	/**
	 * Tells the device supported architecture.
	 * Since mips(/64) has been phased out long ago, is isn't checked here.
	 *
	 * @return ARCH_ARM || ARCH_ARM64 || ARCH_X86 || ARCH_86_64
	 */
	public static int getDeviceArchitecture(){
		if (sProcessArchitecture != UNSUPPORTED_ARCH) {
			return sProcessArchitecture;
		}
		if(isx86Device()){
			return is64BitsDevice() ? ARCH_X86_64 : ARCH_X86;
		}
		return is64BitsDevice() ? ARCH_ARM64 : ARCH_ARM;
	}

	/**
	 * Pins architecture decisions to the ABI Android selected for this process.
	 * This matters when an x86 emulator runs an ARM-only Play build through native
	 * translation: Build.SUPPORTED_ABIS describes the host, while nativeLibraryDir
	 * and every JNI library in this process are ARM.
	 */
	public static void initializeProcessArchitecture(String nativeLibraryDir) {
		int architecture = architectureFromNativeLibraryDir(nativeLibraryDir);
		if (architecture == UNSUPPORTED_ARCH) {
			architecture = archAsInt(System.getProperty("os.arch", ""));
		}
		if (architecture != UNSUPPORTED_ARCH) {
			sProcessArchitecture = architecture;
		}
	}

	static int architectureFromNativeLibraryDir(String nativeLibraryDir) {
		if (nativeLibraryDir == null) {
			return UNSUPPORTED_ARCH;
		}
		String normalized = nativeLibraryDir.toLowerCase().replace('\\', '/');
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		String folderName = normalized.substring(normalized.lastIndexOf('/') + 1);
		if ("arm64".equals(folderName) || "arm64-v8a".equals(folderName)) return ARCH_ARM64;
		if ("arm".equals(folderName) || "armeabi-v7a".equals(folderName)) return ARCH_ARM;
		if ("x86_64".equals(folderName)) return ARCH_X86_64;
		if ("x86".equals(folderName)) return ARCH_X86;
		return UNSUPPORTED_ARCH;
	}

	/**
	 * Tell is the device is based on an x86 processor.
	 * It doesn't tell if the device is 64 or 32 bits.
	 * @return Whether or not the device is x86 based.
	 */
	public static boolean isx86Device(){
		if (sProcessArchitecture != UNSUPPORTED_ARCH) {
			return sProcessArchitecture == ARCH_X86 || sProcessArchitecture == ARCH_X86_64;
		}
		//We check the whole range of supported ABIs,
		//Since asus zenfones can place arm before their native instruction set.
		String[] ABI = is64BitsDevice() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
		int comparedArch = is64BitsDevice() ? ARCH_X86_64 : ARCH_X86;
		for (String str : ABI) {
			if (archAsInt(str) == comparedArch) return true;
		}
		return false;
	}



	/**
	 * Convert an architecture from a String to an int.
	 * @param arch The architecture as a String
	 * @return The architecture as an int, can be UNSUPPORTED_ARCH if unknown.
	 */
	public static int archAsInt(String arch){
		arch = arch.toLowerCase().trim().replace(" ", "");
		if(arch.contains("arm64") || arch.equals("aarch64")) return ARCH_ARM64;
		if(arch.contains("arm") || arch.equals("aarch32")) return ARCH_ARM;
		if(arch.contains("x86_64") || arch.contains("amd64")) return ARCH_X86_64;
		if(arch.contains("x86") || (arch.startsWith("i") && arch.endsWith("86"))) return ARCH_X86;
		//Shouldn't happen
		return UNSUPPORTED_ARCH;
	}

	/**
	 * Convert to a string an architecture.
	 * @param arch The architecture as an int.
	 * @return "arm64" || "arm" || "x86_64" || "x86" || "UNSUPPORTED_ARCH"
	 */
	public static String archAsString(int arch){
		if(arch == ARCH_ARM64) return "arm64";
		if(arch == ARCH_ARM) return "arm";
		if(arch == ARCH_X86_64) return "x86_64";
		if(arch == ARCH_X86) return "x86";
		return "UNSUPPORTED_ARCH";
	}

	/**
	 * Convert to the Android ABI folder name used by packaged native assets.
	 * @param arch The architecture as an int.
	 * @return "arm64-v8a" || "armeabi-v7a" || "x86_64" || "x86" || "UNSUPPORTED_ARCH"
	 */
	public static String archAsStringAndroid(int arch) {
		if(arch == ARCH_ARM64) return "arm64-v8a";
		if(arch == ARCH_ARM) return "armeabi-v7a";
		if(arch == ARCH_X86_64) return "x86_64";
		if(arch == ARCH_X86) return "x86";
		return "UNSUPPORTED_ARCH";
	}

}
