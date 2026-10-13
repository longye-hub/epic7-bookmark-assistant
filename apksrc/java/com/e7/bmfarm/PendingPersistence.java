package com.e7.bmfarm;
import java.io.IOException;
public final class PendingPersistence {
    public interface Store {String read();boolean commit(String value);void retain(String value);}
    private final Store store;
    public PendingPersistence(Store store){this.store=store;}
    public void save(String value)throws IOException{if(!store.commit(value)){store.retain(value);throw new IOException("无法保存确认记录，禁止消费");}}
    public void clear()throws IOException{String previous=store.read();if(!store.commit(null)){store.retain(previous);throw new IOException("无法保存核验结果，待核验记录已保留");}}
}
