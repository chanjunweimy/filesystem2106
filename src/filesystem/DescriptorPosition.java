package filesystem;

public class DescriptorPosition {
	private int _blockIndex;
	private int _blockPosition; 
	private int _blockIntegerNum;
	
	public DescriptorPosition(int blockIndex, 
							  int blockPosition,
							  int blockIntegerNum) {
		setBlockIndex(blockIndex);
		setBlockPosition(blockPosition);
		setBlockIntegerNum(blockIntegerNum);
	}

	public int getBlockIndex() {
		return _blockIndex;
	}

	public int getBlockPosition() {
		return _blockPosition;
	}
	
	private void setBlockIndex(int _blockIndex) {
		this._blockIndex = _blockIndex;
	}

	private void setBlockPosition(int _blockPosition) {
		this._blockPosition = _blockPosition;
	}

	public int getBlockIntegerNum() {
		return _blockIntegerNum;
	}

	public void setBlockIntegerNum(int _blockIntegerNum) {
		this._blockIntegerNum = _blockIntegerNum;
	}
	
}
