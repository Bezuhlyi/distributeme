package org.distributeme.test.roundrobin.client;

import java.util.HashMap;
import java.util.Map;

import net.anotheria.anoprise.metafactory.Extension;
import net.anotheria.anoprise.metafactory.MetaFactory;

import org.distributeme.test.mod.ModedService;
import org.distributeme.test.roundrobin.RoundRobinService;

public class RandomIdClient extends Client{
	public static void main(String a[]) throws Exception{
		RoundRobinService service = MetaFactory.create(RoundRobinService.class, Extension.REMOTE);

		long start = System.currentTimeMillis();
		int errors = 0 ; int replies = 0;
		int LIMIT = 100;
		Map<String, Integer> replyMap = new HashMap<String, Integer>();
		for (int i = 0; i<LIMIT; i++){
			try{
				String randomId = service.getRandomId();
				replies++;
				Integer in = replyMap.get(randomId);
				if (in==null)
					in = 0;
				replyMap.put(randomId, in+1);
			}catch(Exception e){
				errors++;
			}
		}
		
		long end = System.currentTimeMillis();
		
		System.out.println("Tries "+LIMIT+", errors: "+errors+", replies: "+replies+", time: "+(end-start)+" ms");
		System.out.println("RandomId distribution: "+replyMap);
	
	}

}
