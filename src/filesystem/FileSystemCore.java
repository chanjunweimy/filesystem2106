package filesystem;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;

import filesystem.PackableMemory;
import filesystem.DescriptorPosition;
import filesystem.OpenFileRow;
import iosystem.IOSystemCore;
import iosystem.LDiskOutOfBoundaryException;

public class FileSystemCore {
	private static FileSystemCore _fileSystem = null;

	private PackableMemory _packMem = null;
	private IOSystemCore _iosystem = null;
	
	private int[] _mask = null;
	
	private DescriptorPosition[] _descriptorPositions = null;
	private OpenFileRow[] _openFileTable = null;
	
	private static final int BITS_PER_INTEGER = 32;
	private static final int DATA_BLOCK_START = 7;
	
	private static final int INTEGER_PER_DESCRIPTOR = 4;
	private static final int INTEGER_PER_FILE_DIRECTORY = 2;
	
	private static final int OFT_SIZE = 4;
		
	public static FileSystemCore getObject() {
		if (_fileSystem == null) {
			_fileSystem = new FileSystemCore();
		}
		return _fileSystem;
	}
	
	private FileSystemCore() {
		_packMem = PackableMemory.getObject();
		_iosystem = IOSystemCore.getObject();
		_openFileTable = null;

		initializeOpenFileTable();
		initializeDescriptors();
		initializeMask();
	}	
	

	//apis
	public boolean create(String filename) {
		return true;
	}
	
	public boolean destroy(String filename) {
		return true;
	}
	
	public int open(String filename) {
		return 0;
	}
	
	public boolean close(int index) {
		return true;
	}
	
	public byte[] read(int index, int count) {
		byte[] readByte = null;
		return readByte;
	}
	
	public boolean write(int index, int count) {
		return true;
	}
	
	public boolean lseek(int index, int pos) {
		return true;
	}
	
	public String[] directory() {
		String[] directory = null;
		return directory;
	}
	
	public boolean init(String filename) {
		if (!_openFileTable[0].isFree()) {
			return false;
		}
		
		Path dir = Paths.get(filename);
		
		boolean isSuccess;
		try {
		    // Create the empty file with default permissions, etc.
		    Files.createFile(dir);
		    byte[] fileArray = initializeFileArray();
		    isSuccess = initializeLDisk(fileArray);
		    isSuccess = isSuccess && writeFile(dir, fileArray);
		} catch (FileAlreadyExistsException x) {
			isSuccess = loadFile(dir);
		} catch (IOException x) {
			isSuccess = false;
		}
		
		if (isSuccess) {
			_openFileTable[0].setDescriptorIndex(0);
		}
		
		return isSuccess;
	}
	
	public boolean save(String filename) {		
		if (_openFileTable[0].isFree()) {
			return false;
		}
		
		Path dir = Paths.get(filename);
		
		if (!Files.exists(dir)) {
			return false;
		}
		
		for (int i = 0; i < _openFileTable.length; i++) {
			if (!saveOdtBuffer(i)) {
				return false;
			}		
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
		
		return writeFile(dir, fileArray);
	}


	
	//private methods
	private boolean writeFile(Path dir, byte[] fileArray) {
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

	private byte[] initializeFileArray() {
		int fileArrayLength = IOSystemCore.BLOCK_LENGTH * IOSystemCore.BLOCKS_TOTAL_NUMBER;
		byte[] fileArray = new byte[fileArrayLength];
		
		_packMem.setMemory(fileArray);
		
		int[] bitmap = initializeBitmap();
		int[] ldiskInteger = new int[IOSystemCore.BLOCK_LENGTH * 
		                             IOSystemCore.BLOCKS_TOTAL_NUMBER/
		                             PackableMemory.BYTE_PER_INT];
		
		for (int i = 0; i < bitmap.length; i++) {
			ldiskInteger[i] = bitmap[i];
		}
		
		for (int i = bitmap.length; i < ldiskInteger.length; i++) {
			ldiskInteger[i] = 0;
		}
		
		int blockInteger = _descriptorPositions[0].getBlockIntegerNum();
		blockInteger += 1;
		ldiskInteger[blockInteger] = DATA_BLOCK_START;
		
		int bytePosition = 0;
		for (int i = 0; i < ldiskInteger.length; i++) {
			_packMem.pack(ldiskInteger[i], bytePosition);
			bytePosition += PackableMemory.BYTE_PER_INT;
		}
		
		fileArray = _packMem.getMemory();
		
		return fileArray;
	}
	
	private int[] initializeBitmap() {
		int bitmapSize = 	IOSystemCore.BLOCKS_TOTAL_NUMBER /
							( PackableMemory.BIT_PER_BYTE * 
							PackableMemory.BYTE_PER_INT);
		int[] bitmap = new int[bitmapSize];
		
		int bitmapZero = _mask[0];
		for (int i = 1; i <= DATA_BLOCK_START; i++) {
			bitmapZero &= _mask[i];
		}
		bitmap[0] = bitmapZero;
		
		for (int i = 1; i < bitmap.length; i++) {
			bitmap[i] = 0;
		}
		return bitmap;
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
	
	private void initializeMask() {
		_mask = new int[BITS_PER_INTEGER];
		
		int maskLength = _mask.length;
		_mask[maskLength - 1] = 1;
		
		for (int i = maskLength - 2; i >= 0; i--) {
			_mask[i] = _mask[i + 1] << 1;
		}
	}
	
	private void initializeDescriptors() {
		int descriptorNumbers = 	(INTEGER_PER_DESCRIPTOR - 1)
									* IOSystemCore.BLOCK_LENGTH
									/ ( INTEGER_PER_FILE_DIRECTORY
									* PackableMemory.BYTE_PER_INT);
		descriptorNumbers += 1;
		
		int integersPerBlock = 	IOSystemCore.BLOCK_LENGTH / 
								PackableMemory.BYTE_PER_INT;
		
		int blockIndex = 0;
		int blockInteger = integersPerBlock - INTEGER_PER_DESCRIPTOR;
		
		_descriptorPositions = new DescriptorPosition[descriptorNumbers];
		
		for (int i = 0; i < _descriptorPositions.length; i++) {
			int blockPosition = (blockInteger - 1) * PackableMemory.BYTE_PER_INT;
			blockPosition += 1;
			
			_descriptorPositions[i] = new DescriptorPosition(blockIndex, 
														     blockPosition,
														     blockInteger);
			
			blockInteger += INTEGER_PER_DESCRIPTOR;
			
			if (blockInteger >= integersPerBlock){
				blockInteger = 0;
				blockIndex++;
			}
			
		}
	}

	private void initializeOpenFileTable() {
		_openFileTable = new OpenFileRow[OFT_SIZE];
		for (int i = 0; i < _openFileTable.length; i++) {
			_openFileTable[i] = new OpenFileRow(IOSystemCore.BLOCK_LENGTH);
		}
	}

	private boolean saveOdtBuffer(int odtIndex) {
		if (_openFileTable[odtIndex].isFree()) {
			return true;
		}
		
		int descriptorIndex = _openFileTable[odtIndex].getDescriptorIndex();
		int curPosition = _openFileTable[odtIndex].getCurrentPosition();
		
		curPosition /= IOSystemCore.BLOCK_LENGTH;
		curPosition += 1;
		
		int blockIndex = _descriptorPositions[descriptorIndex].getBlockIndex();
		byte[] descriptor = null;
		
		try {
			descriptor = _iosystem.read_block(blockIndex);
		} catch (LDiskOutOfBoundaryException e1) {
			return false;
		}
		
		int position = _descriptorPositions[descriptorIndex].getBlockPosition();
		position += curPosition * PackableMemory.BYTE_PER_INT;
		
		_packMem.setMemory(descriptor);
		blockIndex = _packMem.unpack(position);
		
		if (descriptor == null) {
			return false;
		}
		
		byte[] block = _openFileTable[odtIndex].getBuffer();
		try {
			_iosystem.write_block(blockIndex, block);
		} catch (LDiskOutOfBoundaryException e) {
			return false;
		}
		
		_openFileTable[odtIndex].freeOpenFileRow();
		return true;
	}
}
