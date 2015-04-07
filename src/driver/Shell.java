package driver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Scanner;

import filesystem.FileSystemCore;

public class Shell {
	private Shell() {
	}
	
	private void execute(String[] args) {
		FileSystemCore fileSystem = FileSystemCore.getObject();
		
		if (args == null || args.length == 0) {
			Scanner reader = new Scanner(System.in);
			getUserInputs(fileSystem, reader);
			reader.close();
		} else if (args.length == 2) {
			String fileIn = args[0];
			String fileOut = args[1];
			
			File file = new File(fileIn);
			Scanner reader = null;			
			PrintStream out = null;
			try {
				reader = new Scanner(file);
				out = new PrintStream(new FileOutputStream(fileOut));
				System.setOut(out);
				getUserInputs(fileSystem, reader);
				reader.close();
			} catch (FileNotFoundException e) {
				System.err.println(e.getMessage());
				System.exit(-1);
			}
			
		} else {
			System.exit(-2);
		}
	}

	private void getUserInputs(FileSystemCore fileSystem, Scanner reader) {
		while(reader.hasNextLine()) {
			boolean isSuccess = true;
				
			String input = reader.nextLine();
			input = input.trim();
			
			if (input.isEmpty()) {
				System.out.println("");
				continue;
			}
			
			Scanner analyzer = new Scanner(input);
			String command = "";
			
			if (analyzer.hasNext()) {
				command = analyzer.next();
			}
			StringBuffer feedback = new StringBuffer();
						
			if ("exit".equals(command)) {
				analyzer.close();
				break;
				
			} else if ("cr".equals(command)) {
				String filename = null;
				if (analyzer.hasNext()) {
					filename = analyzer.next();
				} else {
					isSuccess = false;
				}
				
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					isSuccess = fileSystem.create(filename);
					
					feedback.append(filename);
					feedback.append(" created");
				}
			} else if ("de".equals(command)) {
				String filename = null;
				if (analyzer.hasNext()) {
					filename = analyzer.next();
				} else {
					isSuccess = false;
				}
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					isSuccess = fileSystem.destroy(filename);
					
					feedback.append(filename);
					feedback.append(" destroyed");
				}
				
			} else if ("op".equals(command)) {
				String filename = null;
				if (analyzer.hasNext()) {
					filename = analyzer.next();
				} else {
					isSuccess = false;
				}
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					int index = fileSystem.open(filename);
					isSuccess = index >= 0 && index <= FileSystemCore.OFT_SIZE;
					
					feedback.append(filename);
					feedback.append(" opened ");
					feedback.append(index);
				}
			} else if ("cl".equals(command)) {
				int index = FileSystemCore.ERROR_INDEX;
				if (analyzer.hasNext()) {
					index = analyzer.nextInt();
				} else {
					isSuccess = false;
				}
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					isSuccess = fileSystem.close(index);
					feedback.append(index);
					feedback.append(" closed");
				}
				
				
			} else if ("rd".equals(command)) {
				int index = FileSystemCore.ERROR_INDEX;
				int count = FileSystemCore.ERROR_INDEX;
				
				if (analyzer.hasNext()) {
					index = analyzer.nextInt();
				} else {
					isSuccess = false;
				}
				
				if (analyzer.hasNext()) {
					count = analyzer.nextInt();
				} else {
					isSuccess = false;
				}
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					String readString = fileSystem.read(index, count);
					
					if (readString != null) {
						isSuccess = true;
						feedback.append(readString);
					} else {
						isSuccess = false;
					}
				}
			} else if ("wr".equals(command)) {
				int index = FileSystemCore.ERROR_INDEX;
				String writeString = null;
				int count = FileSystemCore.ERROR_INDEX;
				
				if (analyzer.hasNext()) {
					index = analyzer.nextInt();
				} else {
					isSuccess = false;
				}
				if (analyzer.hasNext()) {
					writeString = analyzer.next();
				} else {
					isSuccess = false;
				}
				if (analyzer.hasNext()) {
					count = analyzer.nextInt();
				} else {
					isSuccess = false;
				}
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					isSuccess = fileSystem.write(index, writeString, count);
					feedback.append(count);
					feedback.append(" bytes written");
				}
			} else if ("sk".equals(command)) {
				int index = FileSystemCore.ERROR_INDEX;
				int pos = FileSystemCore.ERROR_INDEX;
				
				if (analyzer.hasNext()) {
					index = analyzer.nextInt();
				} else {
					isSuccess = false;
				}
				if (analyzer.hasNext()) {
					pos = analyzer.nextInt();
				} else {
					isSuccess = false;
				}
				
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					isSuccess = fileSystem.lseek(index, pos);
					
					feedback.append("position is ");
					feedback.append(pos);
				}
			} else if ("dr".equals(command)) {
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					String[] directories = fileSystem.directory();
					if (directories != null) {
						isSuccess = true;
						for (int i = 0; i < directories.length; i++) {
							feedback.append(directories[i]);
							feedback.append(" ");
						}
					} else {
						isSuccess = false;
					}
				}
				
			} else if ("in".equals(command)) {
				String filename = null;
				String msg = null;
				if (analyzer.hasNext()) {
					filename = analyzer.next();
					msg = "disk restored";
				} else {
					filename = "";
					msg = "disk initialized";
				}
				
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					isSuccess = fileSystem.init(filename);
					feedback.append(msg);
				}
			} else if ("sv".equals(command)) {
				String filename = null;
				
				if (analyzer.hasNext()) {
					filename = analyzer.next();
				} else {
					isSuccess = false;
				}
				
				if (analyzer.hasNext()) {
					isSuccess = false;
				}
				
				if (isSuccess) {
					isSuccess = fileSystem.save(filename);
					
					feedback.append("disk saved");
				}
			} else {
				isSuccess = false;
			}
			
			if (!isSuccess) {
				System.out.println("error");
			} else {
				System.out.println(feedback.toString().trim());
			}
			analyzer.close();
		}
	}
	
	public static void main(String[] args) {
		Shell shell = new Shell();
		shell.execute(args);
	}
}
