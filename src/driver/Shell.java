package driver;

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
		
		if (args == null) {
			Scanner reader = new Scanner(System.in);
			getUserInputs(fileSystem, reader);
			reader.close();
		} else if (args.length == 2) {
			String fileIn = args[0];
			String fileOut = args[1];
			
			Scanner reader = new Scanner(fileIn);
			PrintStream out = null;
			try {
				out = new PrintStream(new FileOutputStream(fileOut));
			} catch (FileNotFoundException e) {
				System.err.println(e.getMessage());
				System.exit(-1);
			}
			
			if (out != null) {
				System.setOut(out);
				getUserInputs(fileSystem, reader);
				reader.close();
			}
			
		} else {
			System.exit(-2);
		}
	}

	private void getUserInputs(FileSystemCore fileSystem, Scanner reader) {
		while(true) {
			boolean isSuccess = false;
			
			String input = reader.nextLine();
			input = input.trim();
			
			Scanner analyzer = new Scanner(input);
			String command = analyzer.next();
			StringBuffer feedback = new StringBuffer();
			
			if ("exit".equals(command)) {
				analyzer.close();
				break;
				
			} else if ("cr".equals(command)) {
				String filename = analyzer.next();
				isSuccess = fileSystem.create(filename);
				
				feedback.append(filename);
				feedback.append(" created");
				
			} else if ("de".equals(command)) {
				String filename = analyzer.next();
				isSuccess = fileSystem.destroy(filename);
				
				feedback.append(filename);
				feedback.append(" destroyed");
				
			} else if ("op".equals(command)) {
				String filename = analyzer.next();
				int index = fileSystem.open(filename);
				isSuccess = index >= 0 && index <= FileSystemCore.OFT_SIZE;
				
				feedback.append(filename);
				feedback.append(" opened ");
				feedback.append(index);
			} else if ("cl".equals(command)) {
				int index = analyzer.nextInt();
				isSuccess = fileSystem.close(index);
				
				feedback.append(index);
				feedback.append(" closed");
			} else if ("rd".equals(command)) {
				int index = analyzer.nextInt();
				int count = analyzer.nextInt();
				String readString = fileSystem.read(index, count);
				
				if (readString != null) {
					isSuccess = true;
					feedback.append(readString);
				} else {
					isSuccess = false;
				}
			} else if ("wr".equals(command)) {
				int index = analyzer.nextInt();
				String writeString = analyzer.next();
				int count = analyzer.nextInt();
				
				isSuccess = fileSystem.write(index, writeString, count);
				feedback.append(count);
				feedback.append(" bytes written");
			} else if ("sk".equals(command)) {
				int index = analyzer.nextInt();
				int pos = analyzer.nextInt();
				
				isSuccess = fileSystem.lseek(index, pos);
				
				feedback.append("position is ");
				feedback.append(pos);
			} else if ("dr".equals(command)) {
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
			} else if ("in".equals(command)) {
				String filename = analyzer.next();
				String msg = fileSystem.init(filename);
				if (msg != null) {
					isSuccess = true;
					feedback.append(msg);
				} else {
					isSuccess = false;
				}
			} else if ("sv".equals(command)) {
				String filename = analyzer.next();
				isSuccess = fileSystem.save(filename);
				
				feedback.append("disk saved");
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
