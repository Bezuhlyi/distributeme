package org.distributeme.core.routing;

import net.anotheria.util.StringUtils;
import org.distributeme.core.ClientSideCallContext;
import org.distributeme.core.exception.DistributemeRuntimeException;
import org.distributeme.core.failing.FailDecision;
import org.distributeme.core.failing.FailingStrategy;

import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Abstract implementation of {@link org.distributeme.core.routing.Router} which supports {@link org.distributeme.core.failing.FailingStrategy}.
 * <p/>
 * By methods overriding/implementing router can be configured to support :
 * - Mod or RoundRobin strategy (override properly getStrategy() method  - <p>NOTE : should not return null</p>);
 * - different amounts of service instances, can be configured via annotation ar simply changed by getServerAmount() method override;
 * - support or not support call failing (failingSupported() should return true fro support, false otherwise ).
 * <p/>
 * In case when Mod routing strategy selected for some router, register all MOD - routed methods directly in router-constructor  using next calls:
 * - addModRoutedMethod (name, position) - which will add mod support for method with selected [name], and incoming argument with selected [position] will be used
 * as modable value;
 * - addModRoutedMethod(name) - will add mod support for method with selected [name], and parameter with 0 position will be used as modable (common case).
 * <p/>
 * IMPORTANT : If MOD routing can't be performed for some call (method does not have any incoming params, or incoming params does not matches for modable calculations, or simply we does not need to
 * route some method by MOD ) - RoundRobin will be performed  instead!. For this - just don't call addModRoutedMethod for method which should not be routed by MOD.
 * <p/>
 * <p/>
 * <p/>
 * <p/>
 * By implementing getModableValue(<?>) method - You can simply extract some long from incoming parameter, for further calculations.
 *
 * @author h3llka,dvayanu
 */
public abstract class AbstractRouterWithStickyFailOverToNextNode extends AbstractRouterWithFailover implements ConfigurableRouter, FailingStrategy {

	/**
	 * Services parameter.
	 */
	public static final String PARAMETER_KEY_SERVICES = "services";
	/**
	 * Timeout parameter.
	 */
	public static final String PARAMETER_KEY_TIMEOUT  = "timeout";

	/**
	 * Attribute for call context where we store instances that we already tried.
	 */
	public static final String ATTR_TRIED_INSTANCES = AbstractRouterWithStickyFailOverToNextNode.class.getName()+".instance";

	/**
	 * Random for selection of next instance.
	 */
	private Random random = new Random(System.nanoTime());

	/**
	 * Map with timestamps for last server failures.
	 */
	private ConcurrentMap<String, Long> serverFailureTimestamps = new ConcurrentHashMap<String, Long>();

	@Override
	public FailDecision callFailed(final ClientSideCallContext clientSideCallContext) {
		serverFailureTimestamps.put(clientSideCallContext.getServiceId(), System.currentTimeMillis());
		return super.callFailed(clientSideCallContext);
	}

	@Override
	public String getServiceIdForCall(final ClientSideCallContext clientSideCallContext) {

		if (getLog().isDebugEnabled())
			getLog().debug("Incoming call " + clientSideCallContext);

		if (getServiceAmount() == 0)
			return clientSideCallContext.getServiceId();

		if (failingSupported() && !clientSideCallContext.isFirstCall())
			return getServiceIdForFailing(clientSideCallContext);


		String selectedServiceId = null;

		switch (getStrategy()) {
			case MOD_ROUTER:
				selectedServiceId = getModBasedServiceId(clientSideCallContext);
				break;
			case RR_ROUTER:
				selectedServiceId = getRRBasedServiceId(clientSideCallContext);
				break;
			default:
				throw new AssertionError(" Routing Strategy " + getStrategy() + " not supported in current implementation.");
		}

		Long lastFailed = serverFailureTimestamps.get(selectedServiceId);
		boolean blacklisted = lastFailed != null && (System.currentTimeMillis() - lastFailed) < getConfiguration().getBlacklistTime();

		//the service id we picked up is blacklisted due to previous failing.
		if (blacklisted) {
			clientSideCallContext.setServiceId(selectedServiceId);
			return getServiceIdForFailing(clientSideCallContext);
		}

		return selectedServiceId;
	}


	/**
	 * Return serviceId for failing call.
	 *
	 * @param context {@link org.distributeme.core.ClientSideCallContext}
	 * @return serviceId string
	 */
	private String getServiceIdForFailing(final ClientSideCallContext context) {
		if (getLog().isDebugEnabled())
			getLog().debug("Calculating serviceIdForFailing call. ClientSideCallContext[" + context + "]");

		String originalServiceId = context.getServiceId();
		HashSet<String> instancesThatIAlreadyTried = (HashSet<String>)context.getTransportableCallContext().get(ATTR_TRIED_INSTANCES);
		if (instancesThatIAlreadyTried==null){
			instancesThatIAlreadyTried = new HashSet<String>();
			context.getTransportableCallContext().put(ATTR_TRIED_INSTANCES, instancesThatIAlreadyTried);
		}

		int lastUnderscore = originalServiceId.lastIndexOf(UNDER_LINE);
		String idSubstring = originalServiceId.substring(lastUnderscore + 1);
		instancesThatIAlreadyTried.add(idSubstring);

		//now pick next candidate.
		if (instancesThatIAlreadyTried.size()==getConfiguration().getNumberOfInstances()){
			//we tried everything, it won't work
			throw new DistributemeRuntimeException("No instance available, we tried all already.");
		}

		String result = null;

		if (instancesThatIAlreadyTried.size() == getConfiguration().getNumberOfInstances() -1){
			//only one instance left, it's easier to find it.
			for (int candidate = 0; candidate<getConfiguration().getNumberOfInstances(); candidate++){
				if (!instancesThatIAlreadyTried.contains(""+candidate)){
					//found untried yet
					result = originalServiceId.substring(0, lastUnderscore + 1) + candidate;
				}
			}
		}

		if (result==null){
			//if we are here we have more than one possible candidate.
			int[] candidates = new int[getConfiguration().getNumberOfInstances()-instancesThatIAlreadyTried.size()];
			int i=0;
			for (int candidate = 0; candidate<getConfiguration().getNumberOfInstances(); candidate++) {
				if (!instancesThatIAlreadyTried.contains("" + candidate)) {
			 		candidates[i++] = candidate;
				}
			}
			//now pick a candidate
			int candidate = candidates[random.nextInt(candidates.length)];
			result = originalServiceId.substring(0, lastUnderscore + 1) + candidate;
		}

		if (getLog().isDebugEnabled())
			getLog().debug("serviceIdForFailing result[" + result + "]. ClientSideCallContext[" + context + "]");

		return result;
	}



	@Override
	public void customize(String s) {
		String tokens[] = StringUtils.tokenize(s, ',');
		for (String t : tokens) {
			String key_value[] = StringUtils.tokenize(t, '=');
			String key = key_value[0];
			String value = key_value[1];
			key = key.toLowerCase();
			if (key.equals(PARAMETER_KEY_SERVICES)) {
				try {
					getConfiguration().setNumberOfInstances(Integer.parseInt(value));
				} catch (NumberFormatException e) {
					getLog().error("Can't set customization parameter " +key+ " to " + value + ", send all traffic to default instance");
				}
			}
			if (key.equals(PARAMETER_KEY_TIMEOUT)) {
				try {
					getConfiguration().setBlacklistTime(Long.parseLong(value));
				} catch (NumberFormatException e) {
					getLog().error("Can't set customization parameter " +key+ " to " + value + ", send all traffic to default instance");
				}
			}
		}
		if (getConfiguration().getNumberOfInstances() < 0)
			throw new AssertionError("Customization Error! " + s + " Should be positive value, or at least 0");
	}


}
