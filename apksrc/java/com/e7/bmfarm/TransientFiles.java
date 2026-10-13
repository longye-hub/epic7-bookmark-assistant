package com.e7.bmfarm;
import java.io.File;
import java.io.IOException;
/** Known screenshot/command scratch files only; never touches the transaction journal. */
public final class TransientFiles {
 public static void remove(File file)throws IOException{if(file.exists()&&!file.delete())throw new IOException("临时文件清理失败，已停止："+file.getName());}
 public static void clearKnown(File cache)throws IOException{remove(new File(cache,"e7_f.png"));remove(new File(cache,"root-command.log"));}
 private TransientFiles(){}
}
