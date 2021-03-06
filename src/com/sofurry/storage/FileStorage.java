package com.sofurry.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import com.sofurry.AppConstants;
import com.sofurry.util.Utils;

import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class FileStorage {

	private static boolean mExternalStorageAvailable = false;
	private static boolean mExternalStorageWriteable = false;
	
	private static String pathroot = "/Android/data/com.sofurry/files/";
	
	public static String MUSIC_PATH = "music";
	public static String unusable = "/\\:*+~|<> !?";
	
	/**
	 * Returns the path to the root file folder
	 * @return
	 */
	public static String getPathRoot() {
		return Environment.getExternalStorageDirectory()+pathroot;
	}
	
	/**
	 * Returns the complete filename to the app storage (inside app working/temp/cache dir)
	 * @param filename
	 * The filename to complete with path
	 * @return
	 */
	public static String getPath(String filename) {
		return getPathRoot()+filename;
	}
	
	/**
	 * Returns a readymade fileoutput stream to store the file into
	 * @param filename (absolute file name)
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	public static FileOutputStream getFileOutputStream(String absfilename) throws Exception {
		checkExternalMedia();
		if (!mExternalStorageWriteable) {
			Log.i(AppConstants.TAG_STRING, "FileStorage: External storage not writeable");
			return null;
		}

		// Check if our datapath exists
//		File d = new File(getPath(filename));
		File d = new File(absfilename);
		ensureDirectory(d.getParent());
				
//		File f = new File(getPath(filename));
		File f = new File(absfilename);
		//if (f.canWrite()) {
		//	Log.i("FileStorage", "writing file "+filename);
		FileOutputStream fo = new FileOutputStream(f);
		if (fo == null) {
			Log.d(AppConstants.TAG_STRING, "FO: " + f.getName() + " null");
		}
		
		return fo;
		//}
		//throw new Exception("CanWrite is false, outputstream creation failed.");
	}
	
	/**
	 * Ensures that a Directory exists, and creates it, should it not
	 * @param path
	 * @throws Exception
	 */
	public static void ensureDirectory(String path) throws Exception {
		File d = new File(path);
		if (!d.exists()) d.mkdirs();
	}
	
	/**
	 * Returns true, if the file in question exists
	 * @param filename (absolute file path)
	 * The file's filename
	 * @return
	 * @throws IOException <- what for? if we can't open/access file then it does not exists for us
	 */
	public static boolean fileExists(String absfilename) {
		try {
//			File f = new File(getPath(filename));
			File f = new File(absfilename);
			
			return f.exists();
		} catch (Exception e) {
			return false;
		}
	}

	public static FileInputStream getFileInputStream(String absfilename) throws Exception {
		checkExternalMedia();
		if (!mExternalStorageAvailable) {
			Log.i(AppConstants.TAG_STRING, "FileStorage: External storage not readable");
			return null;
		}
		
		File f = new File(absfilename);
		if (f.canRead()) {
			return new FileInputStream(f);
		} else {
			Log.i(AppConstants.TAG_STRING, "FileStorage: Can't read file "+absfilename);
		}
		return null;
	}
		
	
	/**
	 * Checks if the external media is available
	 */
	private static void checkExternalMedia() throws Exception {
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write the media
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// We can only read the media
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
			throw new Exception("External Storage not Available. (read only)");
		} else {
			// Something else is wrong. It may be one of many other states, but
			// all we need
			// to know is we can neither read nor write
			mExternalStorageAvailable = mExternalStorageWriteable = false;
			throw new Exception("External Storage not Available.");
		}
	}
	
	/**
	 * Returns the root directory of the storage card. Only to be used in exceptions
	 * @return
	 * @throws Exception
	 */
	public static String getExternalMediaRoot() throws Exception {
		checkExternalMedia();
		return "" + Environment.getExternalStorageDirectory();
	}
	
    /**
     * Copies one file from source to destination
     * @param in
     * The file going in
     * @param out
     * The file going out
     * @throws IOException
     */
    public static void copyFile(File in, File out) throws IOException 
    {
    	FileChannel inChannel = new FileInputStream(in).getChannel();
    	FileChannel outChannel = new FileOutputStream(out).getChannel();
    	try {
    		inChannel.transferTo(0, inChannel.size(), outChannel);
    	} 
    	catch (IOException e) {
    		throw e;
    	}
    	finally {
    		if (inChannel != null) inChannel.close();
    		if (outChannel != null) outChannel.close();
    	}
    }
	
    public static void copyFile(String src, String dst) throws Exception {
    	File in = new File(src);
    	if (! in.exists())
    		throw new Exception("Source file not found");
    	
    	File out = new File(dst);
        ensureDirectory(out.getParent());
    	
        copyFile(in, out);
    }
    
	/**
	 * Deletes all contained files in indicated path (absolute path)
	 * @param path
	 * The path to clean e.g. images/
	 * @throws Exception
	 */
	public static void cleanup(String abspath) throws Exception {
//		String abspath = FileStorage.getPath(path);
		
		File f = new File(abspath);
		File[] tokill = f.listFiles();
		if (tokill == null) throw new Exception("Directory to be cleared is not accessible.");
		for (File kill : tokill) {
			if (kill.isDirectory()) continue;
			kill.delete();
		}
	}
	
	public static void cleanold(String abspath, int days) throws Exception {
		File f = new File(abspath);
		File[] tokill = f.listFiles();
		if (tokill == null) throw new Exception("Directory to be cleared is not accessible.");
		for (File kill : tokill) {
			if (kill.isDirectory()) continue;
			if ( (days <= 0) || (System.currentTimeMillis() - kill.lastModified() > ((long)days) * 24*60*60*1000))
				kill.delete();
		}
	}
	
	public static void cleanMusic() throws Exception {
		cleanup(FileStorage.getPath(MUSIC_PATH));
	}
	
	/**
	 * Returns the path that userfiles are saved to.
	 * @param type
	 * The type of files to be saved
	 * @param fname
	 * The filename to be saved
	 * @return
	 * @throws Exception 
	 */
	public static String getUserStorageRoot() throws Exception {
		SharedPreferences sp = Utils.getPreferences(null);
	
		if (sp == null) {
			throw new Exception("FileStorage.getUserStorageRoot: Shared preferences not available"); 
		} else {
			return sp.getString("LibraryRoot", FileStorage.getExternalMediaRoot() + "/SoFurry Files")+"/";
		}
		
//		return FileStorage.getExternalMediaRoot() + "/SoFurry Files/";
	}
	
	public static String getUserStoragePath(String type, String fname) throws Exception {
		return getUserStorageRoot()+type+"/" + sanitize(fname);
	}
	
	/**
	 * Removes Extra characters that will not work well with a filename
	 * @param toSanitize
	 * The string to remove extra characters from
	 * @return
	 */
	public static String sanitize(String toSanitize) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < toSanitize.length(); i++) {
			if (unusable.indexOf(toSanitize.charAt(i)) > -1)
				sb.append("_");
			else
				sb.append(toSanitize.charAt(i));
		}
		return sb.toString();
	}
	
    public static String sanitizeFileName(String fileName) {
        // Remove non-permitted characters
        return sanitizeFileName(fileName, false);
    }

    public static String sanitizeFileName(String fileName, boolean blockDots) {
        if (blockDots) {
            return fileName.replaceAll("[$\\/?%*:.|<>\"]", "_").trim();
        } else {
            return fileName.replaceAll("[$\\/?%*:|<>\"]", "_").trim();
        }
    }

	
}
