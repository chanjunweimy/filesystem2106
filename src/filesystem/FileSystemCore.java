package filesystem;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Vector;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import filesystem.PackableMemory;
import filesystem.DescriptorPosition;
import filesystem.OpenFileRow;
import iosystem.IOSystemCore;
import iosystem.LDiskOutOfBoundaryException;

public class FileSystemCore {
	private static final int BITMAP_BLOCK_INDEX = 0;

	private static FileSystemCore _fileSystem = null;

	private PackableMemory _packMem = null;
	private IOSystemCore _iosystem = null;
	
	private int[] _mask = null;
	
	private int _maxFileLength;
	private int _directoryEntrySize;
	private int _maxFileNum;
	
	private Vector<String> _directoryFileNames = null;
	private HashMap<String, Integer> _filenameAndIndexMap = null;
	
	private DescriptorPosition[] _descriptorPositions = null;
	private OpenFileRow[] _openFileTable = null;
	
	private static final int BITS_PER_INTEGER = 32;
	private static final int DATA_BLOCK_START = 7;
	
	private static final int INTEGER_PER_DESCRIPTOR = 4;
	private static final int INTEGER_PER_FILE_DIRECTORY = 2;
	
	private static final int BLOCK_PER_DESCRIPTOR = 3;
	
	public static final int OFT_SIZE = 4;
	public static final int ERROR_INDEX = -1;
	
	private static final int MAX_FILENAME_LENGTH = 4;
	
	private static final int FILE_SYSTEM_INDEX = 0;
		
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
		_maxFileLength = BLOCK_PER_DESCRIPTOR * IOSystemCore.BLOCK_LENGTH;
		_directoryEntrySize = INTEGER_PER_FILE_DIRECTORY * PackableMemory.BYTE_PER_INT;

		initializeOpenFileTable();
		initializeDescriptors();
		initializeMask();
		
		_directoryFileNames = new Vector<String>(_maxFileNum * 2);
		_filenameAndIndexMap = new HashMap<String, Integer>(_maxFileNum * 2);
	}	
	

	//apis
	public boolean create(String filename) {
		if (filename == null) {
			return false;
		} else if (_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return false;
		} else if (filename.length() > MAX_FILENAME_LENGTH) {
			return false;
		} else if (filename.isEmpty()){
			return false;
		} else if (_directoryFileNames.size() >= _maxFileNum) {
			return false;
		} else if (_filenameAndIndexMap.containsKey(filename)) {
			return false;
		}
		
		
		
		int openFileTableIndex = FILE_SYSTEM_INDEX;
		
		int start = 0;
		if (!lseek(openFileTableIndex, start)){
			return false;
		}

		int freeDescriptorIndex = getAndUpdateFreeDescriptorIndex();
		if (freeDescriptorIndex == ERROR_INDEX) {
			return false;
		}
		
		byte[] saveBytes = retrieveDirEntryByteArray(filename,
				freeDescriptorIndex);
				
		int length = PackableMemory.BYTE_PER_INT * INTEGER_PER_FILE_DIRECTORY;

		byte[] readBytes = new byte[length];
		for (int i = 0; i < readBytes.length; i++) {
			readBytes[i] = 0;
		}
		
		if (!updateDirectoryBuffer(openFileTableIndex, start, length, readBytes,
				saveBytes)) {
			return false;
		}
		
		_directoryFileNames.add(filename);
		_filenameAndIndexMap.put(filename, freeDescriptorIndex);
		
		return true;
	}

	public boolean destroy(String filename) {
		if (filename == null) {
			return false;
		} else if (_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return false;
		} else if (filename.length() > MAX_FILENAME_LENGTH) {
			return false;
		} else if (filename.isEmpty()) {
			return false;
		} else if (_directoryFileNames.size() <= 0) {
			return false;
		} else if (!_filenameAndIndexMap.containsKey(filename)) {
			return false;
		}
		
		int decriptorIndex = _filenameAndIndexMap.get(filename).intValue();
		for (int i = 1; i < _openFileTable.length; i++) {
			if (!_openFileTable[i].isFree()) {
				if (_openFileTable[i].getDescriptorIndex() == decriptorIndex) {
					return false;
				}
			}
		}
		
		int openFileTableIndex = FILE_SYSTEM_INDEX;

		int start = 0;
		if (!lseek(openFileTableIndex, start)){
			return false;
		}
		
		int length = PackableMemory.BYTE_PER_INT * INTEGER_PER_FILE_DIRECTORY;

		byte[] readBytes = retrieveDirEntryByteArray(filename,
				decriptorIndex);
		byte[] saveBytes = new byte[length];
		
		for (int i = 0; i < saveBytes.length; i++) {
			saveBytes[i] = -1;
		}
		
		if (!updateDirectoryBuffer(openFileTableIndex, start, length, readBytes,
				saveBytes)) {
			return false;
		}
		
		if (!clearDescriptor(decriptorIndex)) {
			return false;
		}
		
		_directoryFileNames.remove(filename);
		_filenameAndIndexMap.remove(filename);
		return true;
	}
	
	public int open(String filename) {
		if (_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return ERROR_INDEX;
		} else if (filename == null) {
			return ERROR_INDEX;
		} else if (filename.isEmpty()) {
			return ERROR_INDEX;
		} else if (filename.length() > MAX_FILENAME_LENGTH) {
			return ERROR_INDEX;
		} else if (!_filenameAndIndexMap.containsKey(filename)) {
			return ERROR_INDEX;
		}
		
		for (int i = 1; i < _openFileTable.length; i++) {
			if (!_openFileTable[i].isFree()) {
				int descriptorIndex = _filenameAndIndexMap.get(filename).intValue();
				if (_openFileTable[i].getDescriptorIndex() == descriptorIndex) {
					return ERROR_INDEX;
				}
			}
		}
		
		int index = ERROR_INDEX;
		for (int i = 1; i < _openFileTable.length; i++) {
			if (_openFileTable[i].isFree()) {
				index = i;
				int descriptorIndex = _filenameAndIndexMap.get(filename).intValue();
				_openFileTable[i].setDescriptorIndex(descriptorIndex);	
				break;
			}
		}
		
		return index;
	}
	
	public boolean close(int index) {
		if (_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return false;
		} else if (index == FILE_SYSTEM_INDEX) {
			return false;
		} else if (index < 0 || index >= _openFileTable.length) {
			return false;
		} else if (_openFileTable[index].isFree()) {
			return false;
		}
		
		return closeOdtBuffer(index);
	}
	
	public String read(int index, int count) {		
		if (_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return null;
		} else if (index == FILE_SYSTEM_INDEX || index < 0 || index >= _openFileTable.length) {
			return null;
		} else if (_openFileTable[index].isFree()) {
			return null;
		} else if (count < 0) {
			return null;
		} else if (!prepareOft(index)) {
			return null;
		}
		
		int fileLength = _openFileTable[index].getFileLength();
		int curPos = _openFileTable[index].getCurrentPosition();
		
		if (curPos + count > fileLength) {
			return null;
		} else if (!saveOdtBuffer(index)) {
			return null;
		}
		

				
		StringBuffer readBuffer = new StringBuffer("");
		
		
		while (count > 0) {
			String bufferString = _openFileTable[index].getBufferPartition(count);
			
			if (bufferString == null) {
				return null;
			}
			
			count -= bufferString.length();
			readBuffer.append(bufferString);
			
			if (count <= 0) {
				break;
			}
			
			if (!saveOdtBuffer(index)) {
				return null;
			}
			if (!prepareOft(index)) {
				return null;
			}

		}
		
		return readBuffer.toString();
	}
	
	public boolean write(int index, String writeString, int count) {
		if (_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return false;
		} else if (index == FILE_SYSTEM_INDEX) {
			return false;
		} else if (index < 0 || index >= _openFileTable.length) {
			return false;
		} else if (count < 0) {
			return false;
		} else if (writeString == null) {
			return false;
		} else if (writeString.length() != 1) {
			return false;
		} else if (_openFileTable[index].isFree()) {
			return false;
		}
		
		char[] writeCharArray = writeString.toCharArray();
		if (writeCharArray.length != 1) {
			return false;
		}
		char writeChar = writeCharArray[0];
		byte[] writeBytes = new byte[count];
		for (int i = 0; i < writeBytes.length; i++) {
			writeBytes[i] = (byte) writeChar;
		}
		
		int curPos = _openFileTable[index].getCurrentPosition();
		if (count + curPos > _maxFileLength) {
			return false;
		} else if (!prepareOft(index)) {
			return false;
		}
		
		
		int start = 0;
		int remaining = _openFileTable[index].updateBuffer(writeBytes, start);
		
		while (remaining > 0) {
			if (!saveOdtBuffer(index)) {
				return false;
			}
			if (!prepareOft(index)) {
				return false;
			}
			start = count - remaining;
			remaining = _openFileTable[index].updateBuffer(writeBytes, start);
			
		}
		
		return true;
	}
	
	public boolean lseek(int index, int pos) {
		if (_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return false;
		} else if (index == ERROR_INDEX || pos == ERROR_INDEX){
			return false;
		} else if (index < 0 || index >= _openFileTable.length) {
			return false;
		} else if (_openFileTable[index].isFree()) {
			return false;
		} else if (pos < 0 || pos > _openFileTable[index].getFileLength()) {
			return false;
		} else if (!prepareOft(index)) {
			return false;
		} else if (!saveOdtBuffer(index)) {
			return false;
		} 
		
		int prevPos = _openFileTable[index].getCurrentPosition();
		_openFileTable[index].setCurrentPosition(pos);
		
		if (!updateOdtBufferAndBlock(index)) {
			_openFileTable[index].setCurrentPosition(prevPos);
			return false;
		}
				
		return true;
	}
	
	public String[] directory() {
		if (_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return null;
		} 
		
		String[] directory = new String[_directoryFileNames.size()];
		for (int i = 0; i < directory.length; i++) {
			directory[i] = _directoryFileNames.get(i);
		}
		
		return directory;
	}
	
	public boolean init(String filename) {
		if (filename == null) {
			return false;
		} else if (!_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return false;
		}
		
		Path dir = Paths.get(filename);
		
		boolean isSuccess = true;
		
		if (filename.isEmpty()) {
		    byte[] fileArray = initializeFileArray();
		    isSuccess = initializeLDisk(fileArray);
		} else if (Files.exists(dir)){
			isSuccess = loadFile(dir);
		} else {
			return false;
		}
		
		if (isSuccess) {
			int index = FILE_SYSTEM_INDEX;
			_openFileTable[index].setDescriptorIndex(index);
			
			initializeDirectory(index);
			
			return true;
		}
		
		return false;
	}

	public boolean save(String filename) {	
		if (filename == null) {
			return false;
		} else if (_openFileTable[FILE_SYSTEM_INDEX].isFree()) {
			return false;
		} else if (filename.isEmpty()) {
			return false;
		}
		
		Path dir = Paths.get(filename);
		
		if (!Files.exists(dir)) {
		    try {
				Files.createFile(dir);
			} catch (IOException e) {
				return false;
			}
		}
		
		for (int i = 0; i < _openFileTable.length; i++) {
			if (!closeOdtBuffer(i)) {
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
		
		if (!writeFile(dir, fileArray)) {
			return false;
		}
		
		_directoryFileNames.clear();
		_filenameAndIndexMap.clear();
		return true;
	}


	
	//private methods
	private boolean writeFile(Path dir, byte[] fileArray) {
	    FileOutputStream outputStream = null;
	    File file = dir.toFile();

		try {
			outputStream = new FileOutputStream(file);
			outputStream.write(fileArray);
			outputStream.flush();
		} catch (IOException e) {
			return false;
		} finally {
			if(outputStream != null) {
	            try {
	            	outputStream.close();
	            } catch (IOException e) {
	            	return false;
	            }
			}
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
		
		int blockIntegerStart = _descriptorPositions[0].getBlockIntegerNum() +
								_descriptorPositions[0].getBlockIndex() *
								IOSystemCore.BLOCK_LENGTH /
								PackableMemory.BYTE_PER_INT;
		int blockIntegerEnd = 	_descriptorPositions[_descriptorPositions.length - 1].getBlockIntegerNum() +
								_descriptorPositions[_descriptorPositions.length - 1].getBlockIndex() *
								IOSystemCore.BLOCK_LENGTH /
								PackableMemory.BYTE_PER_INT;
		blockIntegerEnd += 3;
		
		for (int i = blockIntegerStart; i <= blockIntegerEnd; i++) {
			ldiskInteger[i] = 1;
		}
		
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
		for (int i = 1; i < DATA_BLOCK_START; i++) {
			bitmapZero |= _mask[i];
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
		byte[] block = new byte[IOSystemCore.BLOCK_LENGTH];
		
		for (int i = 0; i < fileArray.length; i++) {
		
			block[blockLength] = fileArray[i];
			
			blockLength++;
			if (blockLength == IOSystemCore.BLOCK_LENGTH) {
				try {
					_iosystem.write_block(blockIndex, block);
				} catch (LDiskOutOfBoundaryException e) {
					return false;
				}
				blockIndex++;
				blockLength = 0;
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
		
		_maxFileNum = descriptorNumbers - 1;
		
		int integersPerBlock = 	IOSystemCore.BLOCK_LENGTH / 
								PackableMemory.BYTE_PER_INT;
		
		int blockIndex = 1;
		int blockInteger = 0;
		
		_descriptorPositions = new DescriptorPosition[descriptorNumbers];
		
		for (int i = 0; i < _descriptorPositions.length; i++) {
			int blockPosition = blockInteger * PackableMemory.BYTE_PER_INT;
			
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

	private boolean closeOdtBuffer(int odtIndex) {
		if (!saveOdtBuffer(odtIndex)) {
			return false;
		}
		
		_openFileTable[odtIndex].freeOpenFileRow();
		return true;
	}

	private boolean saveOdtBuffer(int odtIndex) {
		if (odtIndex < 0 || odtIndex >= _openFileTable.length) {
			return false;
		} else if (_openFileTable[odtIndex].isFree()) {
			return true;
		} else if (_openFileTable[odtIndex].hasNoBlock()) {
			return true;
		}
		
		int descriptorIndex = _openFileTable[odtIndex].getDescriptorIndex();
		int fileLength = _openFileTable[odtIndex].getFileLength();
		int blockIndex = _openFileTable[odtIndex].getCurrentBlockIndex();
		byte[] block = _openFileTable[odtIndex].getBuffer();
		
		if (!updateFileLengthInDescriptor(descriptorIndex, fileLength)) {
			return false;
		}

		try {
			_iosystem.write_block(blockIndex, block);
		} catch (LDiskOutOfBoundaryException e) {
			return false;
		}
		return true;
	}

	private boolean updateFileLengthInDescriptor(int descriptorIndex,
			int fileLength) {
		int position = _descriptorPositions[descriptorIndex].getBlockPosition();
		int blockIndex = _descriptorPositions[descriptorIndex].getBlockIndex();
		byte[] descriptor = null;
		
		try {
			descriptor = _iosystem.read_block(blockIndex);
		} catch (LDiskOutOfBoundaryException e1) {
			return false;
		}
		
		if (descriptor == null) {
			return false;
		}
		
		_packMem.setMemory(descriptor);	
		_packMem.pack(fileLength, position);
		descriptor = _packMem.getMemory();
		
		try {
			_iosystem.write_block(blockIndex, descriptor);
		} catch (LDiskOutOfBoundaryException e1) {
			return false;
		}
		return true;
	}
	
	private int getFileLengthInDescriptor(int descriptorIndex) {
		int position = _descriptorPositions[descriptorIndex].getBlockPosition();
		int position2 = position + PackableMemory.BYTE_PER_INT;
		int blockIndex = _descriptorPositions[descriptorIndex].getBlockIndex();
		byte[] descriptor = null;
		int fileLength;
		
		try {
			descriptor = _iosystem.read_block(blockIndex);
		} catch (LDiskOutOfBoundaryException e1) {
			return ERROR_INDEX;
		}
		
		if (descriptor == null) {
			return ERROR_INDEX;
		}
		_packMem.setMemory(descriptor);	
		fileLength = _packMem.unpack(position);
		int firstBlock = _packMem.unpack(position2);
		
		if (fileLength == 1 && firstBlock == 1) {
			fileLength = 0;
			_packMem.pack(fileLength, position);
			descriptor = _packMem.getMemory();
			
			try {
				_iosystem.write_block(blockIndex, descriptor);
			} catch (LDiskOutOfBoundaryException e1) {
				return ERROR_INDEX;
			}
		}
		
		return fileLength;
	}

	private boolean clearDescriptor(int descriptorIndex) {
		int position = _descriptorPositions[descriptorIndex].getBlockPosition();
		int[] positions = new int[BLOCK_PER_DESCRIPTOR];
		positions[0] = position + PackableMemory.BYTE_PER_INT;
		for (int i = 1; i < positions.length; i++) {
			positions[i] = positions[i - 1] + PackableMemory.BYTE_PER_INT;
		}
		
		int blockIndex = _descriptorPositions[descriptorIndex].getBlockIndex();
		byte[] descriptor = null;
		
		try {
			descriptor = _iosystem.read_block(blockIndex);
		} catch (LDiskOutOfBoundaryException e1) {
			return false;
		}
		
		if (descriptor == null) {
			return false;
		}
		_packMem.setMemory(descriptor);	
		int fileLength = _packMem.unpack(position);
		
		int[] blocks = new int[BLOCK_PER_DESCRIPTOR];
		
		for (int i = 0; i < blocks.length; i++) {
			blocks[i] = _packMem.unpack(positions[i]);
			if (blocks[i] != 1) {
				if (!removeBitInBitmap(blocks[i])) {
					return false;
				}
				blocks[i] = 1;
				_packMem.pack(blocks[i], positions[i]);
			}
		}
		
		fileLength = 1;
		_packMem.pack(fileLength, position);
		descriptor = _packMem.getMemory();
		
		try {
			_iosystem.write_block(blockIndex, descriptor);
		} catch (LDiskOutOfBoundaryException e1) {
			return false;
		}
		
		return true;
	}
	
	private boolean removeBitInBitmap(int dataBlockIndex) {
		if (dataBlockIndex < 0) {
			return false;
		}
		
		byte[] bitmap = null;
		try {
			bitmap = _iosystem.read_block(BITMAP_BLOCK_INDEX);
		} catch (LDiskOutOfBoundaryException e1) {
			return false;
		}
		_packMem.setMemory(bitmap);
		
		int firstValMax = BITS_PER_INTEGER;
		int secondValMax = BITS_PER_INTEGER * 2;
		
		int loc = 0;
		
		if (dataBlockIndex <= firstValMax){
			int val1 = _packMem.unpack(loc);
			val1 &= ~_mask[dataBlockIndex];
			_packMem.pack(val1, loc);
		} else if (dataBlockIndex <= secondValMax) {
			loc += PackableMemory.BYTE_PER_INT;
			int val2 = _packMem.unpack(loc);
			val2 &= ~_mask[dataBlockIndex];
			_packMem.pack(val2, loc);
		} else {
			return false;
		}
		
		
		bitmap = _packMem.getMemory();
		try {
			_iosystem.write_block(BITMAP_BLOCK_INDEX, bitmap);
		} catch (LDiskOutOfBoundaryException e) {
			return false;
		}

		return true;
		
	}
	
	
	private boolean updateFileLengthToOft(int openFileTableIndex) {
		int index = _openFileTable[openFileTableIndex].getDescriptorIndex();
		int fileLength = getFileLengthInDescriptor(index);
		
		if (fileLength == ERROR_INDEX) {
			return false;
		}
		
		_openFileTable[openFileTableIndex].setFileLength(fileLength);
		return true;
	}

	private boolean prepareOft(int openFileTableIndex) {
		if (_openFileTable[openFileTableIndex].hasNoBlock()) {
			if (!updateFileLengthToOft(openFileTableIndex)) {
				return false;
			}
			if (!updateOdtBufferAndBlock(openFileTableIndex)) {
				return false;
			}
		} else if (_openFileTable[openFileTableIndex].isFull()) {			
			if (!saveOdtBuffer(openFileTableIndex)) {
				return false;
			} 
			if (!updateOdtBufferAndBlock(openFileTableIndex)) {
				return false;
			}
		}
		return true;
	}

	private boolean updateOdtBufferAndBlock(int openFileTableIndex) {
		int descriptorIndex = _openFileTable[openFileTableIndex].getDescriptorIndex();
		int curPosition = _openFileTable[openFileTableIndex].getCurrentPosition();
		curPosition /= IOSystemCore.BLOCK_LENGTH;
		curPosition += 1;
		int blockIndex = getCurrentBlockFromDescriptor(descriptorIndex, curPosition);
		
		if (blockIndex == 1) {
			return false;
		}
		
		_openFileTable[openFileTableIndex].setCurrentBlockIndex(blockIndex);

		byte[] buffer = null;
		try {
			buffer = _iosystem.read_block(blockIndex);
		} catch (LDiskOutOfBoundaryException e) {
			return false;
		}
		
		if (buffer == null) {
			return false;
		}
		_openFileTable[openFileTableIndex].setBuffer(buffer);
		return true;
	}

	private int getCurrentBlockFromDescriptor(int descriptorIndex,
			int curPosition) {
		int blockIndex = _descriptorPositions[descriptorIndex].getBlockIndex();
		byte[] descriptor = null;
		
		try {
			descriptor = _iosystem.read_block(blockIndex);
		} catch (LDiskOutOfBoundaryException e1) {
			return 1;
		}
		
		if (descriptor == null) {
			return 1;
		}
		
		int position = _descriptorPositions[descriptorIndex].getBlockPosition();
		position += curPosition * PackableMemory.BYTE_PER_INT;
		
		_packMem.setMemory(descriptor);
		
		int dataBlockIndex = _packMem.unpack(position);
		if (dataBlockIndex == 1) {
			dataBlockIndex = allocateFreeBlock();
		} else {
			return dataBlockIndex;
		}
		
		if (dataBlockIndex == 1) {
			return 1;
		}
		
		_packMem.setMemory(descriptor);
		_packMem.pack(dataBlockIndex, position);
		descriptor = _packMem.getMemory();
		
		try {
			_iosystem.write_block(blockIndex, descriptor);
		} catch (LDiskOutOfBoundaryException e1) {
			return 1;
		}
		return dataBlockIndex;
	}

	private int getAndUpdateFreeDescriptorIndex() {
		byte[] descriptor = null;
		int blockIndex;
		int position;
		int freeDescriptorIndex = -1;
		for (int i = 1; i < _descriptorPositions.length; i++) {
			blockIndex = _descriptorPositions[i].getBlockIndex();
			position = _descriptorPositions[i].getBlockPosition();
			try {
				descriptor = _iosystem.read_block(blockIndex);
			} catch (LDiskOutOfBoundaryException e1) {
				return -1;
			}
			int position2 = position + PackableMemory.BYTE_PER_INT;
			
			_packMem.setMemory(descriptor);
			
			int fileLength = _packMem.unpack(position);
			int firstBlock = _packMem.unpack(position2);
			if (fileLength == 1 && firstBlock == 1) {
				fileLength = 0;
				_packMem.pack(fileLength, position);
				descriptor = _packMem.getMemory();
				freeDescriptorIndex = i;
				try {
					_iosystem.write_block(blockIndex, descriptor);
				} catch (LDiskOutOfBoundaryException e1) {
					return -1;
				}
				return freeDescriptorIndex;
			}
		}
		return freeDescriptorIndex;
	}

	
	
	private int allocateFreeBlock() {
		int dataBlockIndex = 1;
		byte[] bitmap = null;
		try {
			bitmap = _iosystem.read_block(BITMAP_BLOCK_INDEX);
		} catch (LDiskOutOfBoundaryException e1) {
			return 1;
		}
		_packMem.setMemory(bitmap);
		
		int loc = 0;
		int val1 = _packMem.unpack(loc);
		
		boolean isDone = false;
		for (int i = DATA_BLOCK_START; i < BITS_PER_INTEGER; i++) {
			int check = val1 & _mask[i];
			if (check == 0) {
				isDone = true;
				dataBlockIndex = i;
				val1 |= _mask[i];
				break;
			}
		}
		
		if (!isDone) {
			loc += PackableMemory.BYTE_PER_INT;
			int val2 = _packMem.unpack(loc);
			for (int i = 0; i < BITS_PER_INTEGER; i++) {
				int index = i + BITS_PER_INTEGER;
				int check = val2 & _mask[index];
				if (check == 0) {
					isDone = true;
					dataBlockIndex = index;
					val2 |= _mask[index];
					break;
				}
			}
			if (isDone) {
				_packMem.pack(val2, loc);
			}
		} else {
			_packMem.pack(val1, loc);
		}
		
		if (isDone) {
			bitmap = _packMem.getMemory();
			try {
				_iosystem.write_block(BITMAP_BLOCK_INDEX, bitmap);
			} catch (LDiskOutOfBoundaryException e) {
				return 1;
			}
		}
		return dataBlockIndex;
	}

	private boolean initializeDirectory(int index) {
		if (!prepareOft(index)) {
			return false;
		}
		
		int systemFileLength = _openFileTable[index].getFileLength();
		if (readOftRow(index, systemFileLength, true) == null) {
			return false;
		}
		return true;
	}

	/**
	 * This method no longer support reading non-directory file
	 * @param index
	 * @param end
	 * @param isDirectory
	 * @return
	 */
	private String readOftRow(int index, int end, boolean isDirectory) {
		if (!isDirectory) {
			return null;
		}
		
		StringBuffer readBuffer = new StringBuffer("");
		
		int bufferLength = _openFileTable[index].getBufferLength();
		int startPoint = _openFileTable[index].getCurrentPosition();
		end = startPoint + end;
		
		int blockNum = (end) / bufferLength;
		if (blockNum < BLOCK_PER_DESCRIPTOR) {
			blockNum++;
		} else if (blockNum > BLOCK_PER_DESCRIPTOR) {
			return null;
		} 
		
		for (int i = 1; i <= blockNum; i++) {
			int length = bufferLength;
			if (i == blockNum) {
				length = end - (blockNum - 1) * bufferLength;
			}
			
			if (length <= startPoint) {
				startPoint -= length;
				continue;
			}
			
			
			int blockIndex = getCurrentBlockFromDescriptor(index, i);
			if (blockIndex == 1) {
				return null;
			}
			
			try {
				byte[] fileBlock = _iosystem.read_block(blockIndex);
				
				_packMem.setMemory(fileBlock);
				Vector <Byte> readBytes = new Vector <Byte>(length);
				int pos = startPoint;
				while (pos < length) {
					if (isDirectory) {
						String readString = readForDirectory(fileBlock, pos);
						readBuffer.append(readString);
						pos += PackableMemory.BYTE_PER_INT;
					} else {
						readBytes.add(fileBlock[pos]);
						pos++;
					}
					
				}
				
				if (!isDirectory) {
					byte[] readBytesArray = new byte[readBytes.size()];
					
					for (int j = 0; j < readBytes.size(); j++) {
						readBytesArray[j] = readBytes.get(j).byteValue();
					}
					readBuffer.append(new String(readBytesArray));
				}
				
				_openFileTable[index].setCurrentBlockIndex(blockIndex);
				_openFileTable[index].setBuffer(fileBlock);
				startPoint = 0;
			} catch (LDiskOutOfBoundaryException e) {
				return null;
			}
		}
		_openFileTable[index].setCurrentPosition(end);
		return readBuffer.toString();
	}

	private String readForDirectory(byte[] fileBlock, int pos) {
		byte[] saveBytes = new byte[PackableMemory.BYTE_PER_INT];
		
		for (int j = 0; j < saveBytes.length; j++) {
			saveBytes[j] = fileBlock[pos];
			pos++;
		}
		
		String directoryFilename = extractFilename(saveBytes);
		int descriptorIndex = _packMem.unpack(pos);
		
		_directoryFileNames.add(directoryFilename);
		_filenameAndIndexMap.put(directoryFilename, descriptorIndex);
		
		return directoryFilename;
	}

	private String extractFilename(byte[] saveBytes) {
		int outLength = saveBytes.length;
		for (int j = saveBytes.length - 1 ; j >= 0; j--) {
			if (saveBytes[j] == -1) {
				outLength--;
			} else {
				break;
			}
		}
				
		byte[] outBytes = new byte[outLength];
		for (int j = 0; j < outBytes.length; j++) {
			outBytes[j] = saveBytes[j];
		}
		
		String output = new String(outBytes);
		return output;
	}

	private byte[] retrieveDirEntryByteArray(String filename,
			int descriptorIndex) {
		byte[] bytes = filename.getBytes(Charset.forName("UTF-8"));
		
		byte[] saveBytes = new byte[_directoryEntrySize];
		
		for (int i = 0; i < saveBytes.length; i++) {
			saveBytes[i] = -1;
		}
		
		for (int i = 0; i < bytes.length; i++) {
			saveBytes[i] = bytes[i];
		}
		int position = PackableMemory.BYTE_PER_INT;
		_packMem.setMemory(saveBytes);
		_packMem.pack(descriptorIndex, position);
		saveBytes = _packMem.getMemory();
		return saveBytes;
	}

	
	private boolean updateDirectoryBuffer(int openFileTableIndex, int start,
			int length, byte[] readBytes, byte[] saveBytes) {
		byte[] buffer = _openFileTable[openFileTableIndex].getBuffer();
		
		while (true) {
			boolean isReplacable = true;
			for (int i = start; i < start + length; i++) {
				if (buffer[i] != readBytes[i]) {
					isReplacable = false;
					break;
				}
			}
			
			if (isReplacable) {
				_openFileTable[openFileTableIndex].updateBuffer(saveBytes, start);
				break;
			}
			start += length;
			_openFileTable[openFileTableIndex].setCurrentPosition(start);
			
			if (!prepareOft(openFileTableIndex)) {
				return false;
			} 
		}
		return true;
	}
}
	
	
