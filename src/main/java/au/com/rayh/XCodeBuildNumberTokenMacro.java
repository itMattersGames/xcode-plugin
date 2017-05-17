package au.com.rayh;

import com.google.common.collect.ListMultimap;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;
import java.util.Map;


@Extension
public class XCodeBuildNumberTokenMacro extends DataBoundTokenMacro {
    @Override
    public String evaluate(AbstractBuild<?, ?> context, TaskListener listener, String macroName)
        throws MacroEvaluationException, IOException, InterruptedException {
        XCodeAction a = context.getAction(XCodeAction.class);
        if(a == null){
            return "";
        }
	return a.getBuildDescription();
    }
    
   
    @Override
    public String evaluate(Run<?, ?> run, FilePath workspace, TaskListener listener, String macroName, Map<String, String> arguments, ListMultimap<String, String> argumentMultimap) throws MacroEvaluationException, IOException, InterruptedException {
 
        XCodeAction a = run.getAction(XCodeAction.class);
        if(a == null){
            return "";
        }
        return a.getBuildDescription();
    }

    @Override
    public boolean acceptsMacroName(String macroName) {
        return macroName.equals("XCODE_BUILD_NUMBER");
    }
}