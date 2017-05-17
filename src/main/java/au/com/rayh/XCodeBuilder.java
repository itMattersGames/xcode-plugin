/*
 * The MIT License
 *
 * Copyright (c) 2011 Ray Yamamoto Hilton
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package au.com.rayh;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.CopyOnWriteList;
import hudson.util.QuotedStringTokenizer;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @author Ray Hilton
 */
public class XCodeBuilder extends Builder implements SimpleBuildStep {

    private static final int SIGTERM = 143;

    private static final String MANIFEST_PLIST_TEMPLATE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">"
            + "<plist version=\"1.0\"><dict><key>items</key><array><dict><key>assets</key><array><dict><key>kind</key><string>software-package</string><key>url</key><string>${IPA_URL_BASE}/${IPA_NAME}</string></dict></array>"
            + "<key>metadata</key><dict><key>bundle-identifier</key><string>${BUNDLE_ID}</string><key>bundle-version</key><string>${BUNDLE_VERSION}</string><key>kind</key><string>software</string><key>title</key><string>${APP_NAME}</string></dict></dict></array></dict></plist>";
    /**
     * @since 1.0
     */
    public Boolean cleanBeforeBuild;
    /**
     * @since 1.3
     */
    public Boolean cleanTestReports;
    /**
     * @since 1.0
     */
    public String configuration;
    /**
     * @since 1.0
     */
    public String target;
    /**
     * @since 1.0
     */
    public String sdk;
    /**
     * @since 1.1
     */
    public String symRoot;
    /**
     * @since 1.2
     */
    public String configurationBuildDir;
    /**
     * @since 1.0
     */
    public String xcodeProjectPath;
    /**
     * @since 1.0
     */
    public String xcodeProjectFile;
    /**
     * @since 1.3
     */
    public String xcodebuildArguments;
    /**
     * @since 1.2
     */
    public String xcodeSchema;
    /**
     * @since 1.2
     */
    public String xcodeWorkspaceFile;
    /**
     * @since 1.0
     */
    public String embeddedProfileFile;
    /**
     * @since 1.0
     */
    public String cfBundleVersionValue;
    /**
     * @since 1.0
     */
    public String cfBundleShortVersionStringValue;
    /**
     * @since 1.0
     */
    public Boolean buildIpa;
    /**
     * @since 1.0
     */
    public Boolean generateArchive;
    /**
     * @since 1.5
     **/
    public Boolean unlockKeychain;
    /**
     * @since 1.4
     */
    public String keychainName;
    /**
     * @since 1.0
     */
    public String keychainPath;
    /**
     * @since 1.0
     */
    public String keychainPwd;
    /**
     * @since 1.3.3
     */
    public String codeSigningIdentity;
    /**
     * @since 1.4
     */
    public Boolean allowFailingBuildResults;
    /**
     * @since 1.4
     */
    public String ipaName;
    /**
     * @since 1.4
     */
    public String ipaOutputDirectory;
    /**
     * @since 1.4
     */
    public Boolean provideApplicationVersion;
    /**
     * @since 1.4
     */
    public Boolean changeBundleID;
    /**
     * @since 1.4
     */
    public String bundleID;
    /**
     * @since 1.4
     */
    public String bundleIDInfoPlistPath;

    public Boolean interpretTargetAsRegEx;
    /**
     * @since 1.5
     */
    public String ipaManifestPlistUrl;

    /**
     * This is a work around for the broken Apple script as documented here:
     http://stackoverflow.com/questions/32504355/error-itms-90339-this-bundle-is-invalid-the-info-plist-contains-an-invalid-ke
     http://stackoverflow.com/questions/32763288/ios-builds-ipa-creation-no-longer-works-from-the-command-line/32845990#32845990
     http://cutting.io/posts/packaging-ios-apps-from-the-command-line/
     */
    public Boolean signIpaOnXcrun;
    
    @DataBoundSetter
    public void setTarget(String target) 
    {
        this.target = target;
    }
        
    @DataBoundSetter
    public void setBuildIpa(Boolean buildIpa) 
    {
        this.buildIpa = buildIpa;
    }
    
    @DataBoundSetter
    public void setGenerateArchive(Boolean generateArchive) 
    {
        this.generateArchive = generateArchive;
    }
    
    @DataBoundSetter
    public void setCleanBeforeBuild(Boolean cleanBeforeBuild) 
    {
        this.cleanBeforeBuild = cleanBeforeBuild;
    }
    
    @DataBoundSetter
    public void setCleanTestReports(Boolean cleanTestReports) 
    {
        this.cleanTestReports = cleanTestReports;
    }
    
    
    @DataBoundSetter
    public void setConfiguration(String configuration) 
    {
        this.configuration = configuration;
    }
    
    @DataBoundSetter
    public void setXcodeProjectPath(String xcodeProjectPath) 
    {
        this.xcodeProjectPath = xcodeProjectPath;
    }
    
    @DataBoundSetter
    public void setXcodeProjectFile(String xcodeProjectFile) 
    {
        this.xcodeProjectFile = xcodeProjectFile;
    }
    
    @DataBoundSetter
    public void setXcodebuildArguments(String xcodebuildArguments) 
    {
        this.xcodebuildArguments = xcodebuildArguments;
    }
    
    @DataBoundSetter
    public void setKeychainName(String keychainName) 
    {
        this.keychainName = keychainName;
    }
    
    @DataBoundSetter
    public void setXcodeWorkspaceFile(String xcodeWorkspaceFile) 
    {
        this.xcodeWorkspaceFile = xcodeWorkspaceFile;
    }
    
    @DataBoundSetter
    public void setXcodeSchema(String xcodeSchema) 
    {
        this.xcodeSchema = xcodeSchema;
    }
    
    @DataBoundSetter
    public void setEmbeddedProfileFile(String embeddedProfileFile) 
    {
        this.embeddedProfileFile = embeddedProfileFile;
    }
    
    @DataBoundSetter
    public void setCodeSigningIdentity(String codeSigningIdentity) 
    {
        this.codeSigningIdentity = codeSigningIdentity;
    }
    
    @DataBoundSetter
    public void setCfBundleVersionValue(String cfBundleVersionValue) 
    {
        this.cfBundleVersionValue = cfBundleVersionValue;
    }
    
    @DataBoundSetter
    public void setCfBundleShortVersionStringValue(String cfBundleShortVersionStringValue) 
    {
        this.cfBundleShortVersionStringValue = cfBundleShortVersionStringValue;
    }
    
    @DataBoundSetter
    public void setUnlockKeychain(Boolean unlockKeychain) 
    {
        this.unlockKeychain = unlockKeychain;
    }
    
    @DataBoundSetter
    public void setKeychainPath(String keychainPath) 
    {
        this.keychainPath = keychainPath;
    }
    
    @DataBoundSetter
    public void seteKeychainPwd(String keychainPwd) 
    {
        this.keychainPwd = keychainPwd;
    }
    
    @DataBoundSetter
    public void setSymRoot(String symRoot) 
    {
        this.symRoot = symRoot;
    }
    
    @DataBoundSetter
    public void setConfigurationBuildDir(String configurationBuildDir) 
    {
        this.configurationBuildDir = configurationBuildDir;
    }
    
    @DataBoundSetter
    public void setAllowFailingBuildResults(Boolean allowFailingBuildResults) 
    {
        this.allowFailingBuildResults = allowFailingBuildResults;
    }
    
    @DataBoundSetter
    public void setIpaName(String ipaName) 
    {
        this.ipaName = ipaName;
    }
    
    @DataBoundSetter
    public void setIpaOutputDirectory(String ipaOutputDirectory) 
    {
        this.ipaOutputDirectory = ipaOutputDirectory;
    }
    
    @DataBoundSetter
    public void setProvideApplicationVersion(Boolean provideApplicationVersion) 
    {
        this.provideApplicationVersion = provideApplicationVersion;
    }
    
    @DataBoundSetter
    public void setChangeBundleID(Boolean changeBundleID) 
    {
        this.changeBundleID = changeBundleID;
    }
    
    @DataBoundSetter
    public void setBundleID(String bundleID) 
    {
        this.bundleID = bundleID;
    }
    
    @DataBoundSetter
    public void setBundleIDInfoPlistPath(String bundleIDInfoPlistPath) 
    {
        this.bundleIDInfoPlistPath = bundleIDInfoPlistPath;
    }
    
    @DataBoundSetter
    public void setInterpretTargetAsRegEx(Boolean interpretTargetAsRegEx) 
    {
        this.interpretTargetAsRegEx = interpretTargetAsRegEx;
    }
    
    @DataBoundSetter
    public void setIpaManifestPlistUrl(String ipaManifestPlistUrl) 
    {
        this.ipaManifestPlistUrl = ipaManifestPlistUrl;
    }
    
    @DataBoundSetter
    public void setSignIpaOnXcrun(Boolean signIpaOnXcrun) 
    {
        this.signIpaOnXcrun = signIpaOnXcrun;
    }
    

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public XCodeBuilder() {}

    @SuppressWarnings("unused")
    private Object readResolve() throws ObjectStreamException {
        if (provideApplicationVersion == null) {
            if (!StringUtils.isEmpty(cfBundleVersionValue)
                || !StringUtils.isEmpty(cfBundleShortVersionStringValue)) {
                provideApplicationVersion = true;
            }
        }
        return this;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, final TaskListener listener) throws InterruptedException, IOException {
        EnvVars envs = build.getEnvironment(listener);
 
        // check that the configured tools exist
        if (!new FilePath(workspace.getChannel(), getGlobalConfiguration().getXcodebuildPath()).exists()) {
            listener.fatalError(Messages.XCodeBuilder_xcodebuildNotFound(getGlobalConfiguration().getXcodebuildPath()));
            throw new AbortException("xcodebuild path is unavailable.");
        }
        if (!new FilePath(workspace.getChannel(), getGlobalConfiguration().getAgvtoolPath()).exists()) {
            listener.fatalError(Messages.XCodeBuilder_avgtoolNotFound(getGlobalConfiguration().getAgvtoolPath()));
            throw new AbortException("Avg tool path is unavailable.");
        }

        // Start expanding all string variables in parameters
        // NOTE: we currently use variable shadowing to avoid having to rewrite all code (and break pull requests), this will be cleaned up at later stage.
        String configuration = envs.expand(this.configuration);
        String target = envs.expand(this.target);
        String sdk = envs.expand(this.sdk);
        String symRoot = envs.expand(this.symRoot);
        String configurationBuildDir = envs.expand(this.configurationBuildDir);
        String xcodeProjectPath = envs.expand(this.xcodeProjectPath);
        String xcodeProjectFile = envs.expand(this.xcodeProjectFile);
        String xcodebuildArguments = envs.expand(this.xcodebuildArguments);
        String xcodeSchema = envs.expand(this.xcodeSchema);
        String xcodeWorkspaceFile = envs.expand(this.xcodeWorkspaceFile);
        String embeddedProfileFile = envs.expand(this.embeddedProfileFile);
        String cfBundleVersionValue = envs.expand(this.cfBundleVersionValue);
        String cfBundleShortVersionStringValue = envs.expand(this.cfBundleShortVersionStringValue);
        String codeSigningIdentity = envs.expand(this.codeSigningIdentity);
        String ipaName = envs.expand(this.ipaName);
        String ipaOutputDirectory = envs.expand(this.ipaOutputDirectory);
        String bundleID = envs.expand(this.bundleID);
        String bundleIDInfoPlistPath = envs.expand(this.bundleIDInfoPlistPath);
        String ipaManifestPlistUrl = envs.expand(this.ipaManifestPlistUrl);
        // End expanding all string variables in parameters  

         FilePath workingDir = workspace;
                 
        // Set the working directory
        if (!StringUtils.isEmpty(xcodeProjectPath)) {
            workingDir = workingDir.child(xcodeProjectPath);
        }
        listener.getLogger().println(Messages.XCodeBuilder_workingDir(workingDir));

        // Infer as best we can the build platform
        String buildPlatform = "iphoneos";
        if (!StringUtils.isEmpty(sdk)) {
            if (StringUtils.contains(sdk.toLowerCase(), "iphonesimulator")) {
                // Building for the simulator
                buildPlatform = "iphonesimulator";
            }
        }

        // Set the build directory and the symRoot
        //
        String symRootValue = null;
        if (!StringUtils.isEmpty(symRoot)) {
            try {
                symRootValue = TokenMacro.expandAll(build, workingDir, listener, symRoot).trim();
            } catch (MacroEvaluationException ex) {
                Logger.getLogger(XCodeBuilder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        String configurationBuildDirValue = null;
        FilePath buildDirectory;
        if (!StringUtils.isEmpty(configurationBuildDir)) {
            try {
                configurationBuildDirValue = TokenMacro.expandAll(build, workingDir, listener, configurationBuildDir).trim();
            } catch (MacroEvaluationException ex) {
                Logger.getLogger(XCodeBuilder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (configurationBuildDirValue != null) {
            // If there is a CONFIGURATION_BUILD_DIR, that overrides any use of SYMROOT. Does not require the build platform and the configuration.
            buildDirectory = new FilePath(workingDir.getChannel(), configurationBuildDirValue);
        } else if (symRootValue != null) {
            // If there is a SYMROOT specified, compute the build directory from that.
            buildDirectory = new FilePath(workingDir.getChannel(), symRootValue).child(configuration + "-" + buildPlatform);
        } else {
            // Assume its a build for the handset, not the simulator.
            buildDirectory = workingDir.child("build").child(configuration + "-" + buildPlatform);
        }
        listener.getLogger().println(Messages.XCodeBuilder_workingDir(buildDirectory));

        // XCode Version
        int returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getXcodebuildPath(), "-version").stdout(listener).pwd(workingDir).join();
        if (returnCode > 0) {
            listener.fatalError(Messages.XCodeBuilder_xcodeVersionNotFound());
            // We fail the build if XCode isn't deployed
            throw new AbortException("XCode not found");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Try to read CFBundleShortVersionString from project
        listener.getLogger().println(Messages.XCodeBuilder_fetchingCFBundleShortVersionString());
        String cfBundleShortVersionString = "";
        returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getAgvtoolPath(), "mvers", "-terse1").stdout(output).pwd(workingDir).join();
        // only use this version number if we found it
        if (returnCode == 0)
            cfBundleShortVersionString = output.toString().trim();
        if (StringUtils.isEmpty(cfBundleShortVersionString))
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringNotFound());
        else
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringFound(cfBundleShortVersionString));
        listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringValue(cfBundleShortVersionString));

        output.reset();

        // Try to read CFBundleVersion from project
        listener.getLogger().println(Messages.XCodeBuilder_fetchingCFBundleVersion());
        String cfBundleVersion = "";
        returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getAgvtoolPath(), "vers", "-terse").stdout(output).pwd(workingDir).join();
        // only use this version number if we found it
        if (returnCode == 0)
            cfBundleVersion = output.toString().trim();
        if (StringUtils.isEmpty(cfBundleVersion))
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionNotFound());
        else
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionFound(cfBundleVersion));
        listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionValue(cfBundleVersion));

        String buildDescription = cfBundleShortVersionString + " (" + cfBundleVersion + ")";
        XCodeAction a = new XCodeAction(buildDescription);
        build.addAction(a);

        // Update the bundle ID
        if (this.changeBundleID != null && this.changeBundleID) {
        	listener.getLogger().println(Messages.XCodeBuilder_CFBundleIdentifierChanged(bundleIDInfoPlistPath, bundleID));
        	returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c",  "Set :CFBundleIdentifier " + bundleID, bundleIDInfoPlistPath).stdout(listener).pwd(workingDir).join();

        	if (returnCode > 0) {
        		listener.fatalError(Messages.XCodeBuilder_CFBundleIdentifierInfoPlistNotFound(bundleIDInfoPlistPath));
        		throw new AbortException(Messages.XCodeBuilder_CFBundleIdentifierInfoPlistNotFound(bundleIDInfoPlistPath));
        	}
        }

        // Update the Marketing version (CFBundleShortVersionString)
        if (this.provideApplicationVersion != null && this.provideApplicationVersion && !StringUtils.isEmpty(cfBundleShortVersionStringValue)) {
            try {
                // Fails the build

                cfBundleShortVersionString = TokenMacro.expandAll(build, workingDir, listener, cfBundleShortVersionStringValue);
            } catch (MacroEvaluationException ex) {
                Logger.getLogger(XCodeBuilder.class.getName()).log(Level.SEVERE, null, ex);
            }
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringUpdate(cfBundleShortVersionString));
            returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getAgvtoolPath(), "new-marketing-version", cfBundleShortVersionString).stdout(listener).pwd(workingDir).join();
            if (returnCode > 0) {
                listener.fatalError(Messages.XCodeBuilder_CFBundleShortVersionStringUpdateError(cfBundleShortVersionString));
               throw new AbortException(Messages.XCodeBuilder_CFBundleShortVersionStringUpdateError(cfBundleShortVersionString));
            }
        }

        // Update the Technical version (CFBundleVersion)
        if (this.provideApplicationVersion != null && this.provideApplicationVersion && !StringUtils.isEmpty(cfBundleVersionValue)) {
            try {
                // Fails the build

                cfBundleVersion = TokenMacro.expandAll(build, workingDir, listener, cfBundleVersionValue);
            } catch (MacroEvaluationException ex) {
                Logger.getLogger(XCodeBuilder.class.getName()).log(Level.SEVERE, null, ex);
            }
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionUpdate(cfBundleVersion));
            returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getAgvtoolPath(), "new-version", "-all", cfBundleVersion).stdout(listener).pwd(workingDir).join();
            if (returnCode > 0) {
                listener.fatalError(Messages.XCodeBuilder_CFBundleVersionUpdateError(cfBundleVersion));
                throw new AbortException(Messages.XCodeBuilder_CFBundleVersionUpdateError(cfBundleVersion));
            }
        }

        listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringUsed(cfBundleShortVersionString));
        listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionUsed(cfBundleVersion));

        // Clean build directories
        if (cleanBeforeBuild) {
            listener.getLogger().println(Messages.XCodeBuilder_cleaningBuildDir(buildDirectory.absolutize().getRemote()));
            buildDirectory.deleteRecursive();
        }

        // remove test-reports and *.ipa
        if (cleanTestReports != null && cleanTestReports) {
            listener.getLogger().println(Messages.XCodeBuilder_cleaningTestReportsDir(workingDir.child("test-reports").absolutize().getRemote()));
            workingDir.child("test-reports").deleteRecursive();
		}

        if (unlockKeychain != null && unlockKeychain) {
            // Let's unlock the keychain
            Keychain keychain = getKeychain();
            if(keychain == null)
            {
                listener.fatalError(Messages.XCodeBuilder_keychainNotConfigured());
                throw new AbortException(Messages.XCodeBuilder_keychainNotConfigured());
            }
            String keychainPath = envs.expand(keychain.getKeychainPath());
            String keychainPwd = envs.expand(keychain.getKeychainPassword());
            launcher.launch().envs(envs).cmds("/usr/bin/security", "list-keychains", "-s", keychainPath).stdout(listener).pwd(workingDir).join();
            launcher.launch().envs(envs).cmds("/usr/bin/security", "default-keychain", "-d", "user", "-s", keychainPath).stdout(listener).pwd(workingDir).join();
            if (StringUtils.isEmpty(keychainPwd))
                returnCode = launcher.launch().envs(envs).cmds("/usr/bin/security", "unlock-keychain", keychainPath).stdout(listener).pwd(workingDir).join();
            else
                returnCode = launcher.launch().envs(envs).cmds("/usr/bin/security", "unlock-keychain", "-p", keychainPwd, keychainPath).masks(false, false, false, true, false).stdout(listener).pwd(workingDir).join();

            if (returnCode > 0) {
                listener.fatalError(Messages.XCodeBuilder_unlockKeychainFailed());
                 throw new AbortException(Messages.XCodeBuilder_unlockKeychainFailed());
            }

            // Show the keychain info after unlocking, if not, OS X will prompt for the keychain password
            launcher.launch().envs(envs).cmds("/usr/bin/security", "show-keychain-info", keychainPath).stdout(listener).pwd(workingDir).join();
        }

        // display useful setup information
        listener.getLogger().println(Messages.XCodeBuilder_DebugInfoLineDelimiter());
        listener.getLogger().println(Messages.XCodeBuilder_DebugInfoAvailablePProfiles());
        /*returnCode =*/ launcher.launch().envs(envs).cmds("/usr/bin/security", "find-identity", "-p", "codesigning", "-v").stdout(listener).pwd(workingDir).join();

        if (!StringUtils.isEmpty(codeSigningIdentity)) {
            listener.getLogger().println(Messages.XCodeBuilder_DebugInfoCanFindPProfile());
            /*returnCode =*/ launcher.launch().envs(envs).cmds("/usr/bin/security", "find-certificate", "-a", "-c", codeSigningIdentity, "-Z", "|", "grep", "^SHA-1").stdout(listener).pwd(workingDir).join();
            // We could fail here, but this doesn't seem to work as it should right now (output not properly redirected. We might need a parser)
        }

        listener.getLogger().println(Messages.XCodeBuilder_DebugInfoAvailableSDKs());
        /*returnCode =*/ launcher.launch().envs(envs).cmds(getGlobalConfiguration().getXcodebuildPath(), "-showsdks").stdout(listener).pwd(workingDir).join();

        XcodeBuildListParser xcodebuildListParser;
        {
            List<String> commandLine = Lists.newArrayList(getGlobalConfiguration().getXcodebuildPath());
            commandLine.add("-list");
            // xcodebuild -list -workspace $workspace
            listener.getLogger().println(Messages.XCodeBuilder_DebugInfoAvailableSchemes());
            if (!StringUtils.isEmpty(xcodeWorkspaceFile)) {
                commandLine.add("-workspace");
                commandLine.add(xcodeWorkspaceFile + ".xcworkspace");
            } else if (!StringUtils.isEmpty(xcodeProjectFile)) {
                commandLine.add("-project");
                commandLine.add(xcodeProjectFile);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            returnCode = launcher.launch().envs(envs).cmds(commandLine).stdout(baos).pwd(workingDir).start().joinWithTimeout(10, TimeUnit.SECONDS, listener);
            String xcodeBuildListOutput = baos.toString("UTF-8");
            listener.getLogger().println(xcodeBuildListOutput);
            boolean timedOut = returnCode == SIGTERM;
            if (returnCode > 0 && !timedOut) return;

            xcodebuildListParser = new XcodeBuildListParser(xcodeBuildListOutput);
        }
        listener.getLogger().println(Messages.XCodeBuilder_DebugInfoLineDelimiter());

        // Build
        StringBuilder xcodeReport = new StringBuilder(Messages.XCodeBuilder_invokeXcodebuild());
        XCodeBuildOutputParser reportGenerator = new JenkinsXCodeBuildOutputParser(workingDir, listener);
        List<String> commandLine = Lists.newArrayList(getGlobalConfiguration().getXcodebuildPath());

        // Prioritizing schema over target setting
        if (!StringUtils.isEmpty(xcodeSchema)) {
            commandLine.add("-scheme");
            commandLine.add(xcodeSchema);
            xcodeReport.append(", scheme: ").append(xcodeSchema);
        } else if (StringUtils.isEmpty(target) && !StringUtils.isEmpty(xcodeProjectFile)) {
            commandLine.add("-alltargets");
            xcodeReport.append("target: ALL");
        } else if(interpretTargetAsRegEx != null && interpretTargetAsRegEx) {
            if(xcodebuildListParser.getTargets().isEmpty()) {
                listener.getLogger().println(Messages.XCodeBuilder_NoTargetsFoundInConfig());
                throw new AbortException(Messages.XCodeBuilder_NoTargetsFoundInConfig());
            }
            Collection<String> matchedTargets = Collections2.filter(xcodebuildListParser.getTargets(),
                    Predicates.containsPattern(target));

            if (matchedTargets.isEmpty()) {
                listener.getLogger().println(Messages.XCodeBuilder_NoMatchingTargetsFound());
                throw new AbortException(Messages.XCodeBuilder_NoMatchingTargetsFound());
            }

            for (String matchedTarget : matchedTargets) {
                commandLine.add("-target");
                commandLine.add(matchedTarget);
                xcodeReport.append("target: ").append(matchedTarget);
            }
        } else {
            commandLine.add("-target");
            commandLine.add(target);
            xcodeReport.append("target: ").append(target);
        }

        if (!StringUtils.isEmpty(sdk)) {
            commandLine.add("-sdk");
            commandLine.add(sdk);
            xcodeReport.append(", sdk: ").append(sdk);
        } else {
            xcodeReport.append(", sdk: DEFAULT");
        }

        // Prioritizing workspace over project setting
        if (!StringUtils.isEmpty(xcodeWorkspaceFile)) {
            commandLine.add("-workspace");
            commandLine.add(xcodeWorkspaceFile + ".xcworkspace");
            xcodeReport.append(", workspace: ").append(xcodeWorkspaceFile);
        } else if (!StringUtils.isEmpty(xcodeProjectFile)) {
            commandLine.add("-project");
            commandLine.add(xcodeProjectFile);
            xcodeReport.append(", project: ").append(xcodeProjectFile);
        } else {
            xcodeReport.append(", project: DEFAULT");
        }

		if (!StringUtils.isEmpty(configuration)) {
			commandLine.add("-configuration");
			commandLine.add(configuration);
			xcodeReport.append(", configuration: ").append(configuration);
		}

        if (cleanBeforeBuild) {
            commandLine.add("clean");
            xcodeReport.append(", clean: YES");
        } else {
            xcodeReport.append(", clean: NO");
        }

        //Bug JENKINS-30362
        //Generating an archive builds the project twice
        //commandLine.add("build");
        if(generateArchive != null && generateArchive){
            commandLine.add("archive");
            xcodeReport.append(", archive:YES");
        }else{
            xcodeReport.append(", archive:NO");
            commandLine.add("build");
        }
        //END Bug JENKINS-30362

        if (!StringUtils.isEmpty(symRootValue)) {
            commandLine.add("SYMROOT=" + symRootValue);
            xcodeReport.append(", symRoot: ").append(symRootValue);
        } else {
            xcodeReport.append(", symRoot: DEFAULT");
        }

        // CONFIGURATION_BUILD_DIR
        if (!StringUtils.isEmpty(configurationBuildDirValue)) {
            commandLine.add("CONFIGURATION_BUILD_DIR=" + configurationBuildDirValue);
            xcodeReport.append(", configurationBuildDir: ").append(configurationBuildDirValue);
        } else {
            xcodeReport.append(", configurationBuildDir: DEFAULT");
        }

        // handle code signing identities
        if (!StringUtils.isEmpty(codeSigningIdentity)) {
            commandLine.add("CODE_SIGN_IDENTITY=" + codeSigningIdentity);
            xcodeReport.append(", codeSignIdentity: ").append(codeSigningIdentity);
        } else {
            xcodeReport.append(", codeSignIdentity: DEFAULT");
        }

        // Additional (custom) xcodebuild arguments
        if (!StringUtils.isEmpty(xcodebuildArguments)) {
            commandLine.addAll(splitXcodeBuildArguments(xcodebuildArguments));
        }

        listener.getLogger().println(xcodeReport.toString());
        returnCode = launcher.launch().envs(envs).cmds(commandLine).stdout(reportGenerator.getOutputStream()).pwd(workingDir).join();
        if (allowFailingBuildResults != null && !allowFailingBuildResults) {
            if (reportGenerator.getExitCode() != 0) return;
            if (returnCode > 0) throw new AbortException();
        }

        // Package IPA
        if (buildIpa) {

            if (!buildDirectory.exists() || !buildDirectory.isDirectory()) {
                listener.fatalError(Messages.XCodeBuilder_NotExistingBuildDirectory(buildDirectory.absolutize().getRemote()));
                throw new AbortException(Messages.XCodeBuilder_NotExistingBuildDirectory(buildDirectory.absolutize().getRemote()));
            }

            // clean IPA
            FilePath ipaOutputPath = null;
            if (ipaOutputDirectory != null && ! StringUtils.isEmpty(ipaOutputDirectory)) {
            	ipaOutputPath = workspace.child(ipaOutputDirectory);

                listener.getLogger().println(Messages.XCodeBuilder_workingDir(ipaOutputPath));
                
            	// Create if non-existent
            	if (! ipaOutputPath.exists()) {
            		ipaOutputPath.mkdirs();
            	}
            }
            
            if (ipaOutputPath == null) {
            	ipaOutputPath = buildDirectory;
            }

            /*listener.getLogger().println(Messages.XCodeBuilder_cleaningIPA());
            for (FilePath path : ipaOutputPath.list("*.ipa")) {
                path.delete();
            }
            listener.getLogger().println(Messages.XCodeBuilder_cleaningDSYM());
            for (FilePath path : ipaOutputPath.list("*-dSYM.zip")) {
                path.delete();
            }*/
            
            // packaging IPA
            listener.getLogger().println(Messages.XCodeBuilder_packagingIPA());
            List<FilePath> apps = buildDirectory.list(new AppFileFilter());
            // FilePath is based on File.listFiles() which can randomly fail | http://stackoverflow.com/questions/3228147/retrieving-the-underlying-error-when-file-listfiles-return-null
            if (apps == null) {
                listener.fatalError(Messages.XCodeBuilder_NoAppsInBuildDirectory(buildDirectory.absolutize().getRemote()));
                 throw new AbortException(Messages.XCodeBuilder_NoAppsInBuildDirectory(buildDirectory.absolutize().getRemote()));
            }

            for (FilePath app : apps) {
                String version = "";
                String shortVersion = "";

                try {
                    output.reset();
                    returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c",  "Print :CFBundleVersion", app.absolutize().child("Info.plist").getRemote()).stdout(output).pwd(workingDir).join();
                    if (returnCode == 0) {
                        version = output.toString().trim();
                    }

                    output.reset();
                    returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleShortVersionString", app.absolutize().child("Info.plist").getRemote()).stdout(output).pwd(workingDir).join();
                    if (returnCode == 0) {
                        shortVersion = output.toString().trim();
                    }
                }
                catch(Exception ex) {
                    listener.getLogger().println("Failed to get version from Info.plist: " + ex.toString());
                    throw new AbortException("Failed to get version from Info.plist: " + ex.toString());
                }

               	if (StringUtils.isEmpty(version) && StringUtils.isEmpty(shortVersion)) {
               		listener.getLogger().println("You have to provide a value for either the marketing or technical version. Found neither.");
               		throw new AbortException("You have to provide a value for either the marketing or technical version. Found neither.");
               	}

                String lastModified = new SimpleDateFormat("yyyy.MM.dd").format(new Date(app.lastModified()));

                String baseName = app.getBaseName().replaceAll(" ", "_") + (shortVersion.isEmpty() ? "" : "-" + shortVersion) + (version.isEmpty() ? "" : "-" + version);
                // If custom .ipa name pattern has been provided, use it and expand version and build date variables
                if (! StringUtils.isEmpty(ipaName)) {
                	EnvVars customVars = new EnvVars(
                		"BASE_NAME", app.getBaseName().replaceAll(" ", "_"),
                		"VERSION", version,
                		"SHORT_VERSION", shortVersion,
                		"BUILD_DATE", lastModified
                	);
                    baseName = customVars.expand(ipaName);
                }

                String ipaFileName = baseName + ".ipa";
                FilePath ipaLocation = ipaOutputPath.child(ipaFileName);

                FilePath payload = ipaOutputPath.child("Payload");
                payload.deleteRecursive();
                payload.mkdirs();

                listener.getLogger().println("Packaging " + app.getBaseName() + ".app => " + ipaLocation.absolutize().getRemote());
                if (buildPlatform.contains("simulator")) {
                    listener.getLogger().println(Messages.XCodeBuilder_warningPackagingIPAForSimulatorSDK(sdk));
                }

                List<String> packageCommandLine = new ArrayList<String>();
                packageCommandLine.add(getGlobalConfiguration().getXcrunPath());
                packageCommandLine.add("-sdk");

                if (!StringUtils.isEmpty(sdk)) {
                    packageCommandLine.add(sdk);
                } else {
                    packageCommandLine.add(buildPlatform);
                }
                packageCommandLine.addAll(Lists.newArrayList("PackageApplication", "-v", app.absolutize().getRemote(), "-o", ipaLocation.absolutize().getRemote()));
                if (!StringUtils.isEmpty(embeddedProfileFile)) {
                    packageCommandLine.add("--embed");
                    packageCommandLine.add(embeddedProfileFile);
                }
                if (!StringUtils.isEmpty(codeSigningIdentity) && signIpaOnXcrun) {
                    packageCommandLine.add("--sign");
                    packageCommandLine.add(codeSigningIdentity);
                }

                returnCode = launcher.launch().envs(envs).stdout(listener).pwd(workingDir).cmds(packageCommandLine).join();
                if (returnCode > 0) {
                    listener.getLogger().println("Failed to build " + ipaLocation.absolutize().getRemote());
                    throw new AbortException("Failed to build " + ipaLocation.absolutize().getRemote());
                }

                // also zip up the symbols, if present
                FilePath dSYM = app.withSuffix(".dSYM");
                if (dSYM.exists()) {
                    returnCode = launcher.launch().envs(envs).stdout(listener).pwd(buildDirectory).cmds("ditto", "-c", "-k", "--keepParent", "-rsrc", dSYM.absolutize().getRemote(), ipaOutputPath.child(baseName + "-dSYM.zip").absolutize().getRemote()).join();
                    if (returnCode > 0) {
                        listener.getLogger().println(Messages.XCodeBuilder_zipFailed(baseName));
                        throw new AbortException(Messages.XCodeBuilder_zipFailed("Failed to build " + ipaLocation.absolutize().getRemote()));
                    }
                }

                if(!StringUtils.isEmpty(ipaManifestPlistUrl)) {
                    FilePath ipaManifestLocation = ipaOutputPath.child(baseName + ".plist");
                    listener.getLogger().println("Creating Manifest Plist => " + ipaManifestLocation.absolutize().getRemote());

                    String displayName = "";
                    String bundleId = "";

                    output.reset();
                    returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleIdentifier", app.absolutize().child("Info.plist").getRemote()).stdout(output).pwd(workingDir).join();
                    if (returnCode == 0) {
                        bundleId = output.toString().trim();
                    }
                    output.reset();
                    returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleDisplayName", app.absolutize().child("Info.plist").getRemote()).stdout(output).pwd(workingDir).join();
                    if (returnCode == 0) {
                        displayName = output.toString().trim();
                    }


                    String manifest = MANIFEST_PLIST_TEMPLATE
                                        .replace("${IPA_URL_BASE}", ipaManifestPlistUrl)
                                        .replace("${IPA_NAME}", ipaFileName)
                                        .replace("${BUNDLE_ID}", bundleId)
                                        .replace("${BUNDLE_VERSION}", shortVersion)
                                        .replace("${APP_NAME}", displayName);

                    ipaManifestLocation.write(manifest, "UTF-8");
                }
                payload.deleteRecursive();
            }
        }
    }

    public Keychain getKeychain() {
        if(!StringUtils.isEmpty(keychainName)) {
            for (Keychain keychain : getGlobalConfiguration().getKeychains()) {
                if(keychain.getKeychainName().equals(keychainName))
                    return keychain;
            }
        }

        if(!StringUtils.isEmpty(keychainPath)) {
            return new Keychain("", keychainPath, keychainPwd, false);
        }

        return null;
    }

    static List<String> splitXcodeBuildArguments(String xcodebuildArguments) {
        if (xcodebuildArguments == null || xcodebuildArguments.length() == 0) {
            return new ArrayList<String>(0);
        }

        final QuotedStringTokenizer tok = new QuotedStringTokenizer(xcodebuildArguments);
        final List<String> result = new ArrayList<String>();
        while(tok.hasMoreTokens())
            result.add(tok.nextToken());

        return result;
    }

    public GlobalConfigurationImpl getGlobalConfiguration() {
    	return getDescriptor().getGlobalConfiguration();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("xcodeBuilder")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	GlobalConfigurationImpl globalConfiguration;

        // backward compatibility
        @Deprecated
        private transient String xcodebuildPath;
        private transient String agvtoolPath;
        private transient String xcrunPath;
        private transient CopyOnWriteList<Keychain> keychains;

        public DescriptorImpl() {
            load();
        }

        @Inject
        void setGlobalConfiguration(GlobalConfigurationImpl c) {
            this.globalConfiguration = c;
            {// data migration from old format
                boolean modified = false;
                if (xcodebuildPath!=null) {
                    c.setXcodebuildPath(xcodebuildPath);
                    modified = true;
                }
                if (agvtoolPath!=null) {
                    c.setAgvtoolPath(agvtoolPath);
                    modified = true;
                }
                if (xcrunPath!=null) {
                    c.setXcrunPath(xcrunPath);
                    modified = true;
                }
                if (keychains!=null) {
                    c.setKeychains(new ArrayList<Keychain>(keychains.getView()));
                    modified = true;
                }
                if (modified) {
                    c.save();
                    save(); // delete the old values from the disk now that the new values are committed
                }
            }
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
		public String getDisplayName() {
			return Messages.XCodeBuilder_xcode();
		}

	    public GlobalConfigurationImpl getGlobalConfiguration() {
	    	return globalConfiguration;
	    }

	    public String getUUID() {
	    	return "" + UUID.randomUUID().getMostSignificantBits();
	    }
    }
}
