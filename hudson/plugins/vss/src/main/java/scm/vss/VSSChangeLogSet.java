package scm.vss;

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.ChangeLogSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

/**
 * 
 * The VSS change log set.
 * 
 * @author vara
 *
 */
public class VSSChangeLogSet extends ChangeLogSet<VSSChangeLogSet.VSSChangeLog>
{
	/**
	 * 
	 * List of history entries.
	 * 
	 */
	private List<VSSChangeLog> history = null;

	/**
	 * 
	 * Log set is created with the change log file.
	 * 
	 * @param changeLogFile Change log file.
	 * @throws IOException, SAXException Error while parsing the log file.
	 * 
	 */
	public VSSChangeLogSet(AbstractBuild build, File changeLogFile)
			throws IOException, SAXException
	{
		super(build);

		history = new ArrayList<VSSChangeLog>();

		//Parse the change log file.
		Digester digester = new Digester();
		digester.setClassLoader(getClass().getClassLoader());
		digester.push(history);
        digester.addObjectCreate("*/entry", VSSChangeLog.class);

        for (String tag : VSSSCM.TAGS) {
            digester.addBeanPropertySetter("*/entry/" + tag);
        }

        digester.addSetNext("*/entry","add");
        digester.parse(changeLogFile);
	}

	/**
	 * 
	 * Returns true if the changes are empty.
	 * 
	 */
	public boolean isEmptySet()
	{
		return history.size() == 0;
	}

	/**
	 * 
	 * Returns the iterator for history entries.
	 * 
	 */
	public Iterator<VSSChangeLog> iterator()
	{
		return history.iterator();
	}

	/**
	 * 
	 * @return Returns the log entries as a list.
	 * 
	 */
	public List getLogs()
	{
		return history;
	}

	/**
	 * 
	 * VSS change log. Wraps over History entry.
	 *
	 */
	public static class VSSChangeLog extends ChangeLogSet.Entry
	{
		/**
		 * 
		 * Max chars to be shown.
		 * 
		 */
		private static final int MAX_CHARS = 40;

		/**
		 * 
		 * The user that has done the change.
		 * 
		 */
		private String user = null;

		/**
		 * 
		 * File version.
		 * 
		 */
		private String version = null;

		/**
		 * 
		 * Action performed on the file.
		 * 
		 */
		private String action = null;

		/**
		 * 
		 * Date on which the action is perfomed on the file.
		 * 
		 */
		private String date = null;

		/**
		 * 
		 * Comment written by the user while perforing action on the file.
		 * 
		 */
		private String comment = null;

		/**
		 * 
		 * File on which action has been performed.
		 * 
		 */
		private String file = null;

		/**
		 * 
		 * @return Action performed on the file.
		 * 
		 */
		public String getAction()
		{
			return action;
		}

		/**
		 * 
		 * @param action Action performed on the file.
		 * 
		 */
		public void setAction(String action)
		{
			this.action = action;
		}

		/**
		 * 
		 * @return Comment written by the user while perforing action on the file.
		 * 
		 */
		public String getComment()
		{
			return comment;
		}

		/**
		 * 
		 * @param comment Comment written by the user while perforing action on 
		 * the file.
		 * 
		 */
		public void setComment(String comment)
		{
			this.comment = comment;
		}

		/**
		 * 
		 * @return Date on which the action is perfomed on the file.
		 * 
		 */
		public String getDate()
		{
			return date;
		}

		/**
		 * 
		 * @param date Date on which the action is perfomed on the file.
		 * 
		 */
		public void setDate(String date)
		{
			this.date = date;
		}

		/**
		 * 
		 * @return File on which action has been performed.
		 * 
		 */
		public String getFile()
		{
			return file;
		}

		/**
		 * 
		 * @param file File on which action has been performed.
		 * 
		 */
		public void setFile(String file)
		{
			this.file = file;
		}

		/**
		 * 
		 * @return The user that has done the change.
		 * 
		 */
		public String getUser()
		{
			return user;
		}

		/**
		 * 
		 * @param user The user that has done the change.
		 * 
		 */
		public void setUser(String user)
		{
			this.user = user;
		}

		/**
		 * 
		 * @return File version.
		 * 
		 */
		public String getVersion()
		{
			return version;
		}

		/**
		 * 
		 * @param version File version.
		 * 
		 */
		public void setVersion(String version)
		{
			this.version = version;
		}

		/**
		 * 
		 * @return The author.
		 * 
		 */
		public User getAuthor()
		{
			return User.get(user);
		}

		/**
		 * 
		 * @return The file name is returned.
		 * 
		 */
		public String getMsg()
		{
			StringBuffer buffer = new StringBuffer();
			filterFromEnd(getFile(), buffer);
			buffer.append(" - ");
			filterFromStart(getComment(), buffer);
			return buffer.toString();
		}

		/**
		 * 
		 * Filters and displays maximum number of chars. Filtering is done in
		 * the end.
		 * 
		 * @param string String to be filtered for certain number of chars.
		 * @param buffer Characters are to be placed into this buffer. 
		 * 
		 */
		private static void filterFromStart(String string, StringBuffer buffer)
		{
			filter(string, MAX_CHARS, buffer, 0, string.length(), 1);
		}

		/**
		 * 
		 * Filters and displays maximum number of chars. Filtering is done in
		 * the start.
		 * 
		 * @param string String to be filtered for certain number of chars.
		 * @param buffer Characters are to be placed into this buffer. 
		 * 
		 */
		private static void filterFromEnd(String string, StringBuffer buffer)
		{
			filter(string, MAX_CHARS, buffer, string.length() - 1, -1, -1);
			buffer.reverse();
		}

		/**
		 * 
		 * Filters as per the arguments given.
		 * 
		 */
		private static void filter(String string, int maxchar, 
				StringBuffer buffer, int start, int end, int incr)
		{
			for(int index = start;index != end; index += incr, maxchar --)
			{
				if(maxchar == 0 || string.charAt(index) == '\n')
				{
					buffer.append("...");
					break;
				}
				buffer.append(string.charAt(index));
			}
		}

		/**
		 * 
		 * Returns the affected files collection.
		 * 
		 */
		public Collection<String> getAffectedPaths()
		{
			return Collections.singletonList(file);
		}
	}
}
