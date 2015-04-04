package iosystem;



public class IOSystemCore {
	private static IOSystemCore _iosystem = null;
	
	private static final int BLOCKS_TOTAL_NUMBER = 64;
	private static final int BLOCK_LENGTH = 64;
	
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
	
	public byte[] read_block(int blockIndex) {
		byte[] block = new byte[BLOCK_LENGTH];
		
		for (int i = 0; i < block.length; i++) {
			block[i] = _ldisk[blockIndex][i];
		}
		
		return block;
	}
	
	public void write_block(int blockIndex, byte[] block) {
		for (int i = 0; i < block.length; i++) {
			_ldisk[blockIndex][i] = block[i];
		}
	}
}
