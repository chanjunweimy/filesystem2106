package iosystem;

import iosystem.LDiskOutOfBoundaryException;

public class IOSystemCore {
	private static IOSystemCore _iosystem = null;
	
	public static final int BLOCKS_TOTAL_NUMBER = 64;
	public static final int BLOCK_LENGTH = 64;
	
	private byte[][] _ldisk = null;
	
	public static IOSystemCore getObject() {
		if (_iosystem == null) {
			_iosystem = new IOSystemCore();
		}
		return _iosystem;
	}
	
	private IOSystemCore() {	
		_ldisk = new byte[BLOCKS_TOTAL_NUMBER][BLOCK_LENGTH];
	}
	
	public byte[] read_block(int blockIndex) throws LDiskOutOfBoundaryException {
		if (blockIndex < 0 || blockIndex >= BLOCKS_TOTAL_NUMBER) {
			throw new LDiskOutOfBoundaryException();
		}
		
		byte[] block = new byte[BLOCK_LENGTH];
		
		for (int i = 0; i < block.length; i++) {
			block[i] = _ldisk[blockIndex][i];
		}
		
		return block;
	}
	
	public void write_block(int blockIndex, byte[] block) throws LDiskOutOfBoundaryException {
		if (blockIndex < 0 || blockIndex >= BLOCKS_TOTAL_NUMBER) {
			throw new LDiskOutOfBoundaryException();
		} else if (block.length > BLOCK_LENGTH) {
			throw new LDiskOutOfBoundaryException();
		}
		
		for (int i = 0; i < block.length; i++) {
			_ldisk[blockIndex][i] = block[i];
		}
	}
}
