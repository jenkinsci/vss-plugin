package scm.vss;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.IOException2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.kohsuke.stapler.StaplerRequest;
import org.xml.sax.SAXException;

import vss.ClassFactory;
import vss.IVSSDatabase;
import vss.IVSSItem;
import vss.IVSSItems;
import vss.IVSSVersion;
import vss.IVSSVersions;
import vss.VSSFlags;

import com4j.Com4jObject;
import com4j.Holder;

/**
 * Manages the content from Microsoft Visual Source Safe.
 * 
 * @author vara
 */
public class VSSSCM extends SCM
{
	/** 
	 * Maximum history entries to be maintained.
	 */
	private static final int MAX_HISTORY_ENTRIES = 100;

	/**
	 * Date format to display the log details.
	 */
	private static final DateFormat DATE_FORMAT = 
						 new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

	/**
	 * The change log XML tags.
	 */
	static final String[] TAGS = new String[]{
			"file", "user", "comment", "action", "date", "version"};

	/**
	 * 
	 * Constant representing deleted type history entry from VSS.
	 * 
	 */
	private static final String DELETED_ACTION = "Deleted";

	/**
	 * Constant representing destroyed type history entry from VSS.
	 */
	private static final String DESTROYED_ACTION = "Destroyed";

	/**
	 * Constant representing added type history entry from VSS.
	 */
	private static final String ADDED_ACTION = "Added";

	/**
	 * Constant representing recovered type history entry from VSS.
	 */
	private static final String RECOVERED_ACTION = "Recovered";

	/**
	 * Path to srcsafe.ini file.
	 */
	private String serverPath = null;

	/**
	 * User name.
	 */
	private String user = null;

	/**
	 * Password.
	 */
	private String password = null;

	/**
	 * Directory path in the VSS server.
	 */
	private String[] vssPaths = null;

	/**
	 * Optional working folder
	 */
	private String workingFolder = null
	
	/**
	 * Indicates whether to keep the files in writable mode or not.
	 */
	private boolean isWritable = false;

	/**
	 * Indicates whether to check/get the files in recursive order or not.
	 */
	private boolean isRecursive = true;

	/**
	 * Indicates whether entire set of files are to be fetched or to get only 
	 * updates from the VSS.
	 */
	private boolean useUpdate = false;

	/**
	 * All the details necessary to get the content from VSS.
	 * 
	 * @param serverPath Path to srcsafe.ini file. 
	 * @param user User name.
	 * @param password Password.
	 * @param vssPath Directory path in the VSS server.
	 * @param isWritable Indicates whether to keep the files in writable mode or 
	 * not.
	 * @param isRecursive Indicates whether to get the files in recursive order
	 * or not.
	 * @param workingFolder the working folder to use
	 */
	public VSSSCM(String serverPath, String user, String password, 
			String vssPath, boolean isWritable, boolean isRecursive, 
			boolean useUpdate, string workingFolder)
	{
		this.serverPath = serverPath;
		this.user = user;
		this.password = password;
		this.vssPaths = vssPath.split(",");
		this.isWritable = isWritable;
		this.isRecursive = isRecursive;
		this.useUpdate = useUpdate;
		this.workingFolder = workingFolder
	}

    /**
	 * 
	 * Module root same as the workspace root.
	 * 
	 */
    @Override
	public FilePath getModuleRoot(FilePath workspace, AbstractBuild build)
	{
		return workspace;
	}


	/**
	 * 
	 * Fetches the content from VSS.
	 * 
	 */
	public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace,
			BuildListener listener, File changelogFile)
			throws IOException, InterruptedException
	{
        // better be on Windows, or else it won't work
        if(launcher.isUnix())
        {
            listener.getLogger().println("[checkout] ERROR : VSS only runs on Windows");
            return false;
        }

		//Are there any builds made before this?
        listener.getLogger().println("[checkout] Checking previous build");
		List<Object[]> historyEntries;
		List<String> deletions = null;
		AbstractBuild lastBuild = (AbstractBuild) build.getPreviousBuild();
		if(lastBuild == null)
		{
			//Get all changes.
			historyEntries = getHistoryEntries(new Date(0), null, listener);
		}
		else
		{
			if(useUpdate)
			{
				deletions = new ArrayList<String>();
			}

			//Get the changes from last build time.
			Date buildTime = lastBuild.getTimestamp().getTime();
			historyEntries = getHistoryEntries(buildTime, deletions, listener);
		
			//Too many changes?
			if(historyEntries.size() >= MAX_HISTORY_ENTRIES)
			{
				deletions = null;
			}
		}
        
		//Clean and refetch the content.
        listener.getLogger().println("[checkout] Cleaning workspace");
		if(deletions != null)
		{
			delete(new File(workspace.toURI()), deletions);
		}
		else
		{
			workspace.deleteContents();
		}

        // we have multiple paths, and want the files in the correct
        // place. So, create the folder structure as well and get the
        // files for each path.
        for (String vssPath : vssPaths)
        {
            // Create the path structure. This is a workaround because
            // I was unable to get source safe to do it for me.
            // Basically, if we have two paths $/path1 and $/path2, we
            // want the workspace to result in /workspaceroot/path1 and
            // /workspaceroot/path2.

            // 1. remove the $/ symbol
            File file = new File(workspace.toURI());
            String localPath = "";
			if (workingFolder.lenth == 0)
			{
				localPath = file.getAbsolutePath() + "/" + vssPath.substring(2);
			}
            else
			{
				localPath = workingFolder;
			}
            // 2. create the folders in the workspace
            try
            {
                (new File(localPath)).mkdirs();
            } catch (Exception e)
            {
                System.err.println("Error: " + e.getMessage());
            }

            // 3. get the files for this path
            get(localPath, vssPath, listener);
        }

        //Persist the changes.
        listener.getLogger().println("[checkout] Saving log file");
		save(changelogFile, historyEntries);

		return true;
	}
			
	/**
	 * Returns the history entries after the start date.
	 * 
	 * @param startDate The date after which the history entries are needed.
	 * @param deletions List of files deleted to collect.
	 * @return The list of history entries.
	 * @throws IOException Any error while getting the history information.
	 * 
	 */
	private List<Object[]> getHistoryEntries(Date startDate, List<String> deletions, TaskListener listener) throws IOException
	{
		return getHistoryEntries(startDate, MAX_HISTORY_ENTRIES, deletions, listener);
	}

	/**
	 * Returns the history entries after the start date. Maximum specified
	 * number of entries will be collected.
	 * 
	 * @param startDate The date after which the history entries are needed.
	 * @param maxEntries Maximum number of entries to be fetched.
	 * @param deletions List of files deleted to collect.
	 * @return The list of history entries.
	 * @throws IOException Any error while getting the history information.
	 * 
	 */
	private List<Object[]> getHistoryEntries(Date startDate, int maxEntries, List<String> deletions, TaskListener listener) throws IOException
	{
        listener.getLogger().println("[history] Getting list of changes since " + startDate);
        
        if(!new File(serverPath).exists())
        {
            throw new IOException(serverPath + " doesn't exist. Configuration error?");
        }

        if(new File(serverPath).isDirectory())
        {
            throw new IOException(serverPath + " is a directory. Please specify the location of srcsafe.ini");
        }

        //Open database.
        IVSSDatabase database;
        try
		{
			database = ClassFactory.createVSSDatabase();
			database.open(serverPath, user, password);
        }
        catch(RuntimeException error)
		{
            listener.getLogger().println("[history] Unable to open database " + serverPath);
			throw new IOException2(error);
		}
            
        try
        {
			//Get history.
            List<Object[]> historyEntries = new ArrayList<Object[]>();
			int historyCount = 0;
			for (String vssPath : vssPaths)
            {    
                IVSSItem vssItem = database.vssItem(vssPath, false);
                int vssLength = vssItem.spec().length();
                int flag;
                if(isRecursive)
                {
                    flag = VSSFlags.VSSFLAG_RECURSYES.comEnumValue();
                }
                else
                {
                    flag = VSSFlags.VSSFLAG_RECURSNO.comEnumValue();
                }
                IVSSVersions versions = vssItem.versions(flag);

                //Loop through and collect the information.
                Iterator iterator = versions.iterator();
                while(historyCount < maxEntries && iterator.hasNext())
                {
                    Com4jObject object = (Com4jObject)iterator.next();
                    IVSSVersion version = object.queryInterface(IVSSVersion.class);

                    //Break off if the history entries are before the given start
                    //date.
                    Date historyDate = version.date();
                    if(historyDate.before(startDate))
                    {
                        version.dispose();
                        object.dispose();
                        break;
                    }

                    //Form the history entry.
                    int versionNo = version.versionNumber();
                    IVSSItem historyItem = version.vssItem();
                    Object[] content = new Object[6];
                    content[0] = historyItem.spec();
                    content[1] = version.username();
                    content[2] = version.comment();
                    content[3] = version.action().trim();
                    content[4] = DATE_FORMAT.format(version.date());
                    content[5] = Integer.toString(versionNo);

                    //Workaround: VSS returns folder name for the files deleted or 
                    //added under it. This is workaround to find files added/deleted
                    //under a folder. Version no can not be 1 for files added or
                    //deleted. This check is only for safety.
                    if(versionNo != 1 && (DELETED_ACTION.equals(content[3]) || 
                       DESTROYED_ACTION.equals(content[3]) || 
                       ADDED_ACTION.equals(content[3]) || 
                       RECOVERED_ACTION.equals(content[3])))
                    {
                        IVSSItem preItem = historyItem.version(versionNo - 1);

                        //Collect files from this version and previous version.
                        Set post = collectItems(historyItem);
                        Set pre  = collectItems(preItem);

                        preItem.dispose();

                        //Collect the added/deleted file to post.
                        if(ADDED_ACTION.equals(content[3]) || 
                           RECOVERED_ACTION.equals(content[3]))
                        {
                            post.removeAll(pre);
                        }
                        else
                        {
                            pre.removeAll(post);
                            post = pre;
                        }

                        //Find out the file added or deleted.
                        Iterator chgIterator = post.iterator();
                        if(chgIterator.hasNext())
                        {
                            content[0] = chgIterator.next();
                        }

                    }

                    //Update deletions. It will be used if useUpdate is set.
                    if(deletions != null && (DELETED_ACTION.equals(content[3]) || 
                            RECOVERED_ACTION.equals(content[3])))
                    {
                        deletions.add(((String)content[0]).substring(vssLength));
                    }

                    //Dispose
                    historyItem.dispose();
                    version.dispose();
                    object.dispose();

                    historyEntries.add(content);

                    historyCount++;
                }

                //Dispose.
                while(iterator.hasNext())
                {
                    Com4jObject object = (Com4jObject)iterator.next();
                    object.dispose();
                }

                versions.dispose();
                vssItem.dispose();
            }
            
			database.dispose();

            listener.getLogger().println("[history] " + historyEntries.size() + " files changed since last build."); 
            
			return historyEntries;
		}
		catch(RuntimeException error)
		{
			//Some COM error.
			throw new IOException2(error);
		}
	}

	/**
	 * 
	 * Saves history entries to the change log file.
	 * 
	 * @param file Change log file.
	 * @param history History entries.
	 * @throws IOException Any error while writing the log file.
	 * 
	 */
	private void save(File file, List<Object[]> history) throws IOException
	{
		PrintWriter stream = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file),"UTF-8"));
		Object[] entry;
		int size = history.size();
		int tagcount = TAGS.length;
		stream.println("<history>");
		for(int index = 0;index < size;index ++)
		{
			stream.println("\t<entry>");
			entry = history.get(index);
			for(int tag = 0;tag < tagcount;tag ++)
			{
				stream.print("\t\t<");
				stream.print(TAGS[tag]);
				stream.print('>');
				stream.print(escapeForXml(entry[tag]));
				stream.print("</");
				stream.print(TAGS[tag]);
				stream.println('>');
			}
			stream.println("\t</entry>");
		}
		stream.println("</history>");
		stream.close();
	}

	/**
	 * Gets the latest from the VSS to the given local path.
	 * 
	 * @param localPath Local directory path where the information has to be
	 * retrieved.
	 * @throws IOException Any error while getting the latest.
	 * 
	 */
	private void get(String localPath, String vssPath, TaskListener listener) throws IOException
	{
        listener.getLogger().println("[get] Getting source code from: " + vssPath);
        
		try
		{
			//Open database.
			IVSSDatabase database = ClassFactory.createVSSDatabase();
			database.open(serverPath, user, password);
            
			//Get the patch to the given VSS path.
			IVSSItem vssItem = database.vssItem(vssPath, false);
			int flags = VSSFlags.VSSFLAG_FORCEDIRNO.comEnumValue();

			//Writable flag for the files fetched.
			if(isWritable)
			{
				flags |= VSSFlags.VSSFLAG_USERRONO.comEnumValue();
			}
			else
			{
				flags |= VSSFlags.VSSFLAG_USERROYES.comEnumValue();
			}

			//Recursive flag to decide whether all subfolders & files to be
			//fetched.
			if(isRecursive)
			{
				flags |= VSSFlags.VSSFLAG_RECURSYES.comEnumValue();
			}
			else
			{
				flags |= VSSFlags.VSSFLAG_RECURSNO.comEnumValue();
			}

			//Get the latest from vss.
            vssItem.get(new Holder<String>(localPath), flags);
            
			//Dispose.
			vssItem.dispose();
			database.dispose();
		}
		catch(RuntimeException error)
		{
			//Some COM error.
			throw new IOException2(error);
		}
	}

	/**
	 * Delete the given list of files.
	 * 
	 * @param workspace Base folder.
	 * @param deletions Files to be deleted recursively.
	 * 
	 */
	private void delete(File workspace, List<String> deletions)
	{
        for (String deletion : deletions)
        {
            File file = new File(workspace,deletion);
            if(file.exists())
            {
                try
                {
                    Util.deleteRecursive(file);
                }
                catch(IOException e)
                {
                    //Just ignore the error.
                }
            }
        }
    }

	/**
	 * Converts the input in the way that it can be written to the XML.
	 * Special characters are converted to XML understandable way.
	 * 
	 * @param object The object to be escaped.
	 * @return Escaped string that can be written to XML.
	 */
	public static String escapeForXml(Object object)
	{
		if(object == null)
		{
			return null;
		}

		//Loop through and replace the special chars.
		String string = object.toString();
		int size = string.length();
		char ch;
        StringBuilder escapedString = new StringBuilder(size);
		for(int index = 0;index < size;index ++)
		{
			//Convert special chars.
			ch = string.charAt(index);
			switch(ch)
			{
				case '&'  : escapedString.append("&amp;");	break;
				case '<'  : escapedString.append("&lt;");	break;
				case '>'  : escapedString.append("&gt;");	break;
				case '\'' : escapedString.append("&apos;");	break;
				case '\"' : escapedString.append("&quot;");break;
				default: 	escapedString.append(ch);
			}
		}

		return escapedString.toString();
	}

	/**
	 * Collects the sub items from the folder given.
	 * 
	 * @param folder Folder to be checked.
	 * @return The set of sub items from the folder.
	 */
	private Set collectItems(IVSSItem folder)
	{
		//Get the items from the folder.
		IVSSItems items = folder.items(false);
		Iterator iterator = items.iterator();
		Set<String> itemSet = new HashSet<String>(items.count());

		//Just copy the items to a set.
		while(iterator.hasNext())
		{
            Com4jObject object = (Com4jObject)iterator.next();
            IVSSItem item = object.queryInterface(IVSSItem.class);
			itemSet.add(item.spec());
            
            // dispose
            item.dispose();
		}
        
        // dispose
        // according to other code in this class, when iterator.hasNext is
        // false you don't need to dispose the iterator.
        items.dispose();
        
		return itemSet;
	}

	/**
	 * 
	 * Returns the change log parser.
	 * 
	 */
	public ChangeLogParser createChangeLogParser()
	{
		return new VSSChangeLogParser();
	}

	//Attributes.
	/**
	 * 
	 * @return The writable flag.
	 * 
	 */
	public boolean isWritable()
	{
		return isWritable;
	}

	/**
	 * 
	 * @return The recursive flag.
	 * 
	 */
	public boolean isRecursive()
	{
		return isRecursive;
	}

	/**
	 * 
	 * @return The useUpdate flag.
	 * 
	 */
	public boolean isUseUpdate()
	{
		return useUpdate;
	}

	/**
	 * 
	 * @return The password.
	 * 
	 */
	public String getPassword()
	{
		return password;
	}

	/**
	 * 
	 * @return The WorkingFolder.
	 * 
	 */
	public String getWorkingFolder()
	{
		return workinFolder;
	}

	/**
	 * 
	 * @return VSS srcsafe.ini path.
	 * 
	 */
	public String getServerPath()
	{
		return serverPath;
	}

	/**
	 * 
	 * @return The user name.
	 * 
	 */
	public String getUser()
	{
		return user;
	}

	/**
	 * 
	 * @return The VSS path.
	 * 
	 */
	public String getVssPaths()
	{
        if (vssPaths.length == 0)
        {
            return "";
        }

        if (vssPaths.length == 1)
        {
            return vssPaths[0];
        }

        // Join the paths together
        String joinedVssPaths = "";
        for (String vssPath : vssPaths)
        {
            joinedVssPaths = joinedVssPaths + "," + vssPath;
        }

        // Crude method above
        // So let's remove the ",path,path" first comma
        if (joinedVssPaths.startsWith(",", 0))
        {
            joinedVssPaths = joinedVssPaths.substring(1);
        }

		return joinedVssPaths;
	}

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> ab,
                                                   Launcher lnchr,
                                                   TaskListener tl) 
                                                  throws IOException,
                                                  InterruptedException
    {
        return SCMRevisionState.NONE;
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project,
                                                      Launcher lnchr,
                                                      FilePath fp,
                                                      TaskListener tl,
                                                      SCMRevisionState scmrs)
                                                     throws IOException,
                                                     InterruptedException
    {
        //If this is the build then it deserves a build.
		AbstractBuild<?, ?> lastBuild = (AbstractBuild<?,?>)project.getLastBuild();
		if(lastBuild == null)
		{
			tl.getLogger().println("[poll] Last Build : #" + lastBuild.getNumber());
		}
		else
		{
			// If we've never built before, well, gotta build!
            tl.getLogger().println("[poll] No previous build, so forcing an initial build.");
            return PollingResult.BUILD_NOW;
		}
        
        Date buildTime = lastBuild.getTimestamp().getTime();
		
        if(getHistoryEntries(buildTime, 1, null, tl).isEmpty())
        {
            tl.getLogger().println("[poll] No changes found in repository.");
            return PollingResult.NO_CHANGES;
        }
        else
        {
            tl.getLogger().println("[poll] Changes found in repository.");
            return PollingResult.SIGNIFICANT;
        }        
    }

	/**
	 * 
	 * The VSS change log parser.
	 * 
	 */
	private static class VSSChangeLogParser extends ChangeLogParser
	{
		/**
		 * 
		 * Parses and returns the change log details.
		 * 
		 */
		public VSSChangeLogSet parse(AbstractBuild build, File changeLogFile)
				throws IOException, SAXException
		{
			return new VSSChangeLogSet(build, changeLogFile);
		}
		
	}

	/**
	 * 
	 * VSS descriptor that describes about the VSS SCM.
	 * 
	 */
    @Extension
	public static class VSSDescriptor extends SCMDescriptor<VSSSCM>
	{
		/**
		 * 
		 * Passes the necessary information to Descriptor.
		 *
		 */
		public VSSDescriptor()
		{
			super(VSSSCM.class, null);
			load();
		}

		/**
		 * 
		 * @return The display name.
		 * 
		 */
		public String getDisplayName()
		{
			return "Visual Source Safe";
		}

		/**
		 * 
		 * @return New instance of VSS SCM.
		 * 
		 */
        @Override
		public VSSSCM newInstance(StaplerRequest req,
                                  net.sf.json.JSONObject formData) throws FormException
		{
			return new VSSSCM(
					req.getParameter("server_path"), 
					req.getParameter("user"),
					req.getParameter("password"),
					req.getParameter("vss_path"),
					req.getParameter("writable") != null,
					req.getParameter("recursive") != null,
					req.getParameter("useupdate") != null);
					req.getParameter("workingfolder")
		}
	}
}