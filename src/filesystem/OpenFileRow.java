package filesystem;

public class OpenFileRow {
	private byte[] _buffer = null; 
	private int _currentPosition;
	private int _descriptorIndex ;
	private int _fileLength;
	private int _currentBlockIndex;
	private boolean _isStart;
	
	private int _bufferLength;
	
	private static final int NOT_FREE_INDEX = -1;
	
	public OpenFileRow(int bufferLength) {
		_bufferLength = bufferLength;
		
		freeOpenFileRow();
	}
	
	public void freeOpenFileRow() {
		_buffer = new byte[_bufferLength];
		setCurrentPosition(0);
		setFileLength(0);
		setDescriptorIndex(NOT_FREE_INDEX);
		setCurrentBlockIndex(NOT_FREE_INDEX);
		_isStart = false;
	}
	
	public boolean isFree() {
		return _descriptorIndex == NOT_FREE_INDEX;
	}
	
	public boolean isFull() {
		return _currentPosition / _bufferLength > 0 &&
			   _currentPosition % _bufferLength == 0;
	}
	
	public boolean isFileEnded() {
		return _fileLength <= _currentPosition;
	}
	
	public boolean hasNoBlock() {
		return _currentBlockIndex == NOT_FREE_INDEX;
	}
	
	public byte[] getBuffer() {
		return _buffer;
	}
	
	public int updateBuffer(byte[] buffer, int startPoint) {
		int size = buffer.length - startPoint;
		if (isFull() && !_isStart) {
			return size;
		}
		_isStart = false;
		
		int myBufferPosition = _currentPosition % _bufferLength;
		int myBufferSpace = _bufferLength - myBufferPosition;
		
		if (size > myBufferSpace) {
			_currentPosition += myBufferSpace;
			size -= myBufferSpace;
			
			for (int i = startPoint; i < startPoint + myBufferSpace; i++) {
				_buffer[myBufferPosition] = buffer[i];
				myBufferPosition++;
			}
		} else {
			_currentPosition += size;
			size = 0;
			
			for (int i = startPoint; i < buffer.length; i++) {
				_buffer[myBufferPosition] = buffer[i];
				myBufferPosition++;
			}
		}
				
		if (_currentPosition > _fileLength) {
			setFileLength(_currentPosition);
		}
		
		return size;
	}
	
	public String getBufferPartition(int count) {
		if (_currentPosition + count > _fileLength) {
			return null;
		}
		
		byte[] bufferPartition = null;
		int size = count;
		if (isFull() && !_isStart) {
			return null;
		}
		_isStart = false;
		
		int myBufferPosition = _currentPosition % _bufferLength;
		int myBufferSpace = _bufferLength - myBufferPosition;
		
		if (size > myBufferSpace) {
			_currentPosition += myBufferSpace;
			bufferPartition = new byte[myBufferSpace];		
		} else {
			_currentPosition += size;
			bufferPartition = new byte[size];
		}
		
		
		for (int i = 0; i < bufferPartition.length; i++) {
			bufferPartition[i] = _buffer[myBufferPosition];
			myBufferPosition++;
		}
		String ret = new String(bufferPartition);
		
		return ret;
	}
	
	public void setBuffer(byte[] buffer) {
		this._buffer = buffer;
	}
	
	public int getCurrentPosition() {
		return _currentPosition;
	}
	
	public void setCurrentPosition(int currentPosition) {
		this._currentPosition = currentPosition;
	}
	
	public int getDescriptorIndex() {
		return _descriptorIndex;
	}
	
	public void setDescriptorIndex(int descriptorIndex) {
		this._descriptorIndex = descriptorIndex;
	}
	
	public int getFileLength() {
		return _fileLength;
	}
	
	public void setFileLength(int fileLength) {
		this._fileLength = fileLength;
	}

	public int getCurrentBlockIndex() {
		return _currentBlockIndex;
	}

	public void setCurrentBlockIndex(int currentBlockIndex) {
		_isStart = true;
		this._currentBlockIndex = currentBlockIndex;
	}
	
	public int getBufferLength() {
		return _bufferLength;
	}
}
