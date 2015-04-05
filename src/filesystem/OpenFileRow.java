package filesystem;

public class OpenFileRow {
	private byte[] _buffer = null; 
	private int _currentPosition;
	private int _descriptorIndex ;
	private int _fileLength;
	
	private int _bufferLength;
	
	private static final int NOT_FREE_INDEX = -1;
	
	public OpenFileRow(int bufferLength) {
		_bufferLength = bufferLength;
		
		freeOpenFileRow();
	}
	
	public void freeOpenFileRow() {
		_buffer = new byte[_bufferLength];
		_currentPosition = 0;
		_fileLength = 0;
		_descriptorIndex = NOT_FREE_INDEX;
	}
	
	public boolean isFree() {
		return _descriptorIndex == NOT_FREE_INDEX;
	}
	
	public byte[] getBuffer() {
		return _buffer;
	}
	public void setBuffer(byte[] _buffer) {
		this._buffer = _buffer;
	}
	public int getCurrentPosition() {
		return _currentPosition;
	}
	public void setCurrentPosition(int _currentPosition) {
		this._currentPosition = _currentPosition;
	}
	public int getDescriptorIndex() {
		return _descriptorIndex;
	}
	public void setDescriptorIndex(int _descriptorIndex) {
		this._descriptorIndex = _descriptorIndex;
	}
	public int getFileLength() {
		return _fileLength;
	}
	public void setFileLength(int _fileLength) {
		this._fileLength = _fileLength;
	}
	
	
	
}
