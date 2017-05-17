package au.com.rayh;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.collect.Lists;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.tasks.SimpleBuildWrapper;

@SuppressWarnings("rawtypes")
public class OSXKeychainBuildWrapper extends SimpleBuildWrapper {
	@DataBoundConstructor
	public OSXKeychainBuildWrapper() {
		
	}
	
    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {

            listener.getLogger().println("[OS X] restore keychains as defined in global configuration");

            EnvVars envs = build.getEnvironment(listener);

            List<String> commandLine = Lists.newArrayList("/usr/bin/security");
            commandLine.add("list-keychains");
            commandLine.add("-s");

            String defaultKeychainName = getDescriptor().getGlobalConfiguration().getDefaultKeychain();
            Keychain defaultKeychain = null;
            for (Keychain k : getDescriptor().getGlobalConfiguration().getKeychains()) {
                if (k.isInSearchPath() && ! StringUtils.isEmpty(k.getKeychainPath())) {
                        commandLine.add(envs.expand(k.getKeychainPath()));

                        if (defaultKeychain == null && defaultKeychainName != null && k.getKeychainName().equals(defaultKeychainName)) {
                                defaultKeychain = k;
                        }
                }
            }

            int returnCode = launcher.launch().envs(envs).cmds(commandLine).stdout(listener).pwd(workspace).join();

            // Set default keychain
            if (returnCode == 0 && defaultKeychain != null) {
                returnCode = launcher.launch().envs(envs).cmds("/usr/bin/security", "default-keychain", "-d", "user", "-s", envs.expand(defaultKeychain.getKeychainPath())).stdout(listener).pwd(workspace).join();
            }

            // Something went wrong, mark unstable to ping user
            if (returnCode > 0) {
                build.setResult(Result.UNSTABLE);
            }
	}
	
        @Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}
	
	@Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		/**
		 * Obtain the global configuration
		 */
		@Inject
		private GlobalConfigurationImpl globalConfiguration; 
		
		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return Messages.OSXKeychainBuildWrapper_restoreOSXKeychainsAfterBuildProcessAsDefinedInGlobalConfiguration();
		}
		
		public GlobalConfigurationImpl getGlobalConfiguration() {
			return globalConfiguration;
		}
	}
}
