package filesystem;

//This class contains implementations of methods to 
//-- pack an integer into 4 consecutive bytes of a byte array
//-- unpack an integer from 4 consecutive bytes of a byte array
//-- exhaustively test the pack and unpack methods.
//
//This file should be saved as Packable_memory.java.  Once it has been
//compiled, the tester can be invoked by typing "java Packable_memory"

class PackableMemory {
	private byte[] _mem = null;

	private static PackableMemory _packMem = null;
	
	private static final int MASK = 0xff;
	
	public static final int BIT_PER_BYTE = 8;
	public static final int BYTE_PER_INT = 4;
	
	protected static PackableMemory getObject() {
		if (_packMem == null) {
			_packMem = new PackableMemory();
		}
		return _packMem;
	}
	
	private PackableMemory() {
	}
	
	protected void setMemory(byte[] mem) {
		this._mem = mem;
	}
	
	protected byte[] getMemory() {
		return _mem;
	}

	// Pack the 4-byte integer val into the four bytes _mem[loc]..._mem[loc+3].
	// The most significant porion of the integer is stored in _mem[loc].
	// Bytes are masked out of the integer and stored in the array, working
	// from right(least significant) to left (most significant).
	protected void pack(int val, int loc) {
		
		for (int i = BYTE_PER_INT - 1; i >= 0; i--) {
			_mem[loc + i] = (byte) (val & MASK);
			val = val >> BIT_PER_BYTE;
		}
	}

	// Unpack the four bytes _mem[loc]..._mem[loc+3] into a 4-byte integer,
	// and return the resulting integer value.
	// The most significant porion of the integer is stored in _mem[loc].
	// Bytes are 'OR'ed into the integer, working from left (most significant)
	// to right (least significant)
	protected int unpack(int loc) {
		int v = (int) _mem[loc] & MASK;
		for (int i = 1; i < BYTE_PER_INT; i++) {
			v = v << BIT_PER_BYTE;
			v = v | ((int) _mem[loc + i] & MASK);
		}
		return v;
	}

}