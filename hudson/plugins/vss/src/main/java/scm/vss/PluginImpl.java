package scm.vss;

import hudson.Plugin;
import hudson.scm.SCMS;

/**
 * 
 * Microsoft VSS plugin. Suppors SCM for VSS.
 * 
 * @author vara
 *
 */
public class PluginImpl extends Plugin
{
	/**
	 * 
	 * Registers VSS SCM.
	 * 
	 */
	public void start() throws Exception
	{
		SCMS.SCMS.add(VSSSCM.DESCRIPTOR);
		super.start();
	}
}
