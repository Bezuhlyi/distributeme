package org.distributeme.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
public class RegistryUtilTest {

	@Test public void testRegistryParentUtil() {
		String host = "test.host.uk";
		int port = 815;
		String baseUrl = RegistryUtil.getRegistryBaseUrl(host, port);
		System.out.println("Base Url: "+baseUrl);
		assertEquals(baseUrl, "http://"+host+":"+port+"/distributeme/registry/");
	}
	
}
