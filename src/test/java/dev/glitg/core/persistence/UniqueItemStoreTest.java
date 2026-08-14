package dev.glitg.core.persistence;

import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UniqueItemStoreTest {
    @Test void simultaneousAllocationsNeverExceedLimit() throws Exception {var store=new InMemoryUniqueItemStore();var executor=Executors.newFixedThreadPool(12);var success=new AtomicInteger();for(int i=0;i<100;i++)executor.submit(()->{if(store.allocate("mace",10,1).allocated())success.incrementAndGet();});executor.shutdown();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));assertEquals(10,success.get());assertEquals(10,store.used("mace"));}
}
