package com.shawinc.jenkins.plugin.userbuildsstartedaction;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.RunParameterValue;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;


public class UserBuildsStartedAction extends UserProperty implements Action
{
	private Map<String,List<Integer>> map = null;

	@DataBoundConstructor
	@SuppressWarnings("LeakingThisInConstructor")
	public UserBuildsStartedAction(User user) {
		this.user = user;
		this.map = new HashMap<String,List<Integer>>();

	}

	public final String getRootPath()
	{
		return Stapler.getCurrentRequest().getRootPath();
	}

	public User getUser() {
		return user;
	}

	public Map<Job,List<Integer>> getRuns()
	{
		Map aMap = new HashMap<Job,int[]>();
		Iterator<Entry<String,List<Integer>>> itr = map.entrySet().iterator();
		while (itr.hasNext())
		{
			Entry entry = itr.next();
			Job job = (Job) Hudson.getInstance().getItem((String)entry.getKey());
			aMap.put(job, ((List)entry.getValue()).toArray());
		}
		return aMap;
	}

	public void addRun(Run run) throws IOException
	{
		Job job = run.getParent();
		List runs = map.get(job.getName());
		if (runs == null)
		{
			runs = new ArrayList<Run>();
			map.put(job.getName(), runs);
		}
		runs.add(new Integer(run.number));
		user.save();
	}

	public final List<String> getParameters(Run run)
	{
		List<String> list = new ArrayList<String>();
		if (run != null)
		{
			ParametersAction pa = (ParametersAction)run.getAction(ParametersAction.class);
			if (pa != null )
			{
				List<ParameterValue> params = pa.getParameters();
				if (params != null && params.size() > 0)
				{
					Iterator<ParameterValue> itr = params.iterator();
					while (itr.hasNext())
					{
						ParameterValue val = itr.next();
						if (val instanceof RunParameterValue)
						{
							RunParameterValue rpv = (RunParameterValue)val;
							list.add(rpv.getName() + " = " + rpv.getRunId());
						}
						else if (val instanceof hudson.model.FileParameterValue)
						{
							hudson.model.FileParameterValue fpv = (hudson.model.FileParameterValue) val;
							list.add("file upload: " + fpv.getOriginalFileName());
						}
						else
							list.add(val.toString().replaceFirst("\\(.*\\)", ""));
					}
				}
			}
		}
		return list;
	}

	public void removeRun(Run run) throws IOException
	{
		List runs = map.get(run.getParent().getName());
		if (runs != null)
		{
			runs.remove(new Integer(run.number));
			user.save();
		}
	}

	public String getIconFileName() {
		return "clock.png";
	}

	public String getDisplayName() {
		return "Builds started";
	}

	public String getUrlName() {
		return "buildsStarted";
	}

	@Extension
	public static final class DescriptorImpl extends UserPropertyDescriptor
	{
		@Override
		public UserProperty newInstance(User user) {
			return new UserBuildsStartedAction(user);
		}

		@Override
		public String getDisplayName() {
			return "";
		}
	}

	@Extension
	public static final class ListenerImpl extends RunListener<Run>
	{
		@Override
		public void onDeleted(Run r)
		{
			Cause.UserIdCause cause = (Cause.UserIdCause)r.getCause(Cause.UserIdCause.class);
			if (cause != null)
			{
				User u = Hudson.getInstance().getUser(cause.getUserId());
				UserBuildsStartedAction ubsa = u.getProperty(UserBuildsStartedAction.class);
				if (ubsa != null)
				{
					try {
						ubsa.removeRun(r);
					} catch (IOException ex) {
						Logger.getLogger(UserBuildsStartedAction.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			}
		}

		@Override
		public void onCompleted(Run r, TaskListener listener)
		{
			Cause.UserIdCause cause = (Cause.UserIdCause)r.getCause(Cause.UserIdCause.class);
			if (cause != null)
			{
				User u = Hudson.getInstance().getUser(cause.getUserId());
				UserBuildsStartedAction ubsa = u.getProperty(UserBuildsStartedAction.class);
				if (ubsa == null)
				{
					ubsa = new UserBuildsStartedAction(u);
				}
				try {
					ubsa.addRun(r);
				} catch (IOException ex) {
					Logger.getLogger(UserBuildsStartedAction.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}
}
