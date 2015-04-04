package filesystem;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;

import filesystem.PackableMemory;
import iosystem.IOSystemCore;
import iosystem.LDiskOutOfBoundaryException;

public class FileSystemCore {
	private static FileSystemCore _fileSystem = null;

	private PackableMemory _packMem = null;
	private IOSystemCore _iosystem = null;
	
	public static FileSystemCore getObject() {
		if (_fileSystem == null) {
			_fileSystem = new FileSystemCore();
		}
		return _fileSystem;
	}
	
	private FileSystemCore() {
		_packMem = PackableMemory.getObject();
		_iosystem = IOSystemCore.getObject();
	}
	
	public boolean init(String filename) {
		Path dir = Paths.get(filename);
		try {
		    // Create the empty file with default permissions, etc.
		    Files.createFile(dir);
		} catch (FileAlreadyExistsException x) {
			return loadFile(dir);
		} catch (IOException x) {
			return false;
		}
		
		return true;
	}
	
	public boolean save(String filename) {
		Path dir = Paths.get(filename);
		
		if (!Files.exists(dir)) {
			return false;
		}
		
		byte[] fileArray = null;
		try {
			fileArray = getAllLDiskDatas();
		} catch (LDiskOutOfBoundaryException e) {
			return false;
		}
		
		if (fileArray == null) {
			return false;
		}
		
		try {
			Files.write(dir, fileArray);
		} catch (IOException e) {
			return false;
		}
		
		return true;
	}

	private boolean loadFile(Path dir) {
		byte[] fileArray;
		try {
			fileArray = Files.readAllBytes(dir);
			return initializeLDisk(fileArray);
			
		} catch (IOException e) {
			return false;
		}
	}

	private boolean initializeLDisk(byte[] fileArray) {
		int blockIndex = 0;
		int blockLength = 0;
		byte[] block = null;
		
		for (int i = 0; i < fileArray.length; i++) {
			if (block == null) {
				block = new byte[IOSystemCore.BLOCK_LENGTH];
			}
			
			block[blockLength] = fileArray[i];
			
			if (blockLength == IOSystemCore.BLOCK_LENGTH) {
				try {
					_iosystem.write_block(blockIndex, block);
				} catch (LDiskOutOfBoundaryException e) {
					return false;
				}
				blockIndex++;
				blockLength = 0;
				block = null;
			}
		}
		return true;
	}
	
	private byte[] getAllLDiskDatas() throws LDiskOutOfBoundaryException {
		int fileArrayLength = IOSystemCore.BLOCK_LENGTH * IOSystemCore.BLOCKS_TOTAL_NUMBER;
		byte[] fileArray = new byte[fileArrayLength];
		
		int fileArrayIndex = 0;
		for (int blockIndex = 0; blockIndex < IOSystemCore.BLOCK_LENGTH; blockIndex++) {
			byte[] block = _iosystem.read_block(blockIndex);
			
			for (int blockLength = 0; blockLength < block.length; blockLength++) {
				fileArray[fileArrayIndex] = block[blockLength];
				fileArrayIndex++;
			}
		}
		
		return fileArray;
	}
	
}
