package org.distributeme.core.routing;

import org.distributeme.core.ClientSideCallContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

/**
 * TODO comment this class
 *
 * @author lrosenberg
 * @since 30.10.12 15:53
 */
public class RoundRobinRouterWithFOToNextConcurrentTest {

	private static Logger log = LoggerFactory.getLogger(RoundRobinRouterWithFOToNextConcurrentTest.class);

	@Test
	public void testConcurrentAccess(){
		int threadCount = 5;
		final int limit = 1000000;
		final CountDownLatch ready = new CountDownLatch(threadCount);
		final CountDownLatch go = new CountDownLatch(1);
		final CountDownLatch finish = new CountDownLatch(threadCount);

		final AtomicLong requestCounter0 = new AtomicLong(0);
		final AtomicLong requestCounter1 = new AtomicLong(0);
		final AtomicLong requestCounter2 = new AtomicLong(0);
		final AtomicLong wtf = new AtomicLong(0);

		final RoundRobinRouterWithFailoverToNextNode router = new RoundRobinRouterWithFailoverToNextNode();
		router.customize("3");
		final ClientSideCallContext context = new ClientSideCallContext("service", "method", new ArrayList<Object>(0));



		for (int i=0; i<threadCount; i++){
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					ready.countDown();
					try{
						go.await();
					}catch(InterruptedException ignored){}
					for (int x = 0; x<limit; x++){
						String id = router.getServiceIdForCall(context);
						if (id.equals("service_0"))
							requestCounter0.incrementAndGet();
						else if (id.equals("service_1"))
							requestCounter1.incrementAndGet();
						else if (id.equals("service_2"))
							requestCounter2.incrementAndGet();
						else
							wtf.incrementAndGet();
					}//for
					finish.countDown();
				}
			});
			t.start();
		}

		try{
			ready.await();
		}catch(InterruptedException ignore){}

		go.countDown();

		try{
			finish.await();
		}catch(InterruptedException ignore){}

		log.debug("Finished: ");
		log.debug("1: "+requestCounter0.get());
		log.debug("2: "+requestCounter1.get());
		log.debug("3: "+requestCounter2.get());
		long total = requestCounter0.get()+requestCounter1.get()+requestCounter2.get()  ;
		long trueTotal = threadCount * limit;
		log.debug("TOTAL: "+total);
		log.debug("WTF: "+wtf);
		log.debug("first % "+(requestCounter0.get()*100/total));
		log.debug("second % "+(requestCounter1.get()*100/total));
		log.debug("third % "+(requestCounter2.get()*100/total));


		System.out.println("Finished: ");
		System.out.println("1: " + requestCounter0.get());
		System.out.println("2: " + requestCounter1.get());
		System.out.println("3: " + requestCounter2.get());
		System.out.println("TOTAL: " + total);
		System.out.println("WTF: " + wtf);
		System.out.println("first % " + (requestCounter0.get() * 100 / total));
		System.out.println("second % " + (requestCounter1.get() * 100 / total));
		System.out.println("third % " + (requestCounter2.get() * 100 / total));
		System.out.println("wtf % " + (wtf.get() * 100 / trueTotal));

		assertEquals(0, wtf.get());


	}
}
