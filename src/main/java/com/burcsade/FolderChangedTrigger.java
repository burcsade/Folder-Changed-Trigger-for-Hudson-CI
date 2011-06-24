package com.burcsade;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.io.File;
import java.io.FileFilter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author bsade
 */
public class FolderChangedTrigger<Job> extends Trigger {

    private String _folderToMonitor;
    private boolean _recursiveCheck;
    private String _excludedFilesExp;
    private Item _project;
    private HashMap<String, Long> _fileMap;

    @DataBoundConstructor
    public FolderChangedTrigger(String folderToMonitor, boolean recursiveCheck, String excludedFilesExp) throws ANTLRException {
        super("* * * * *");
        this._folderToMonitor = folderToMonitor;
        this._recursiveCheck = recursiveCheck;
        this._excludedFilesExp = excludedFilesExp;
    }
    
    public String getExcludedFilesExp() {
        return _excludedFilesExp;
    }

    public String getFolderToMonitor() {
        return _folderToMonitor;
    }

    public boolean isRecursiveCheck() {
        return _recursiveCheck;
    }

    @Override
    public void run()
    {
        if (_folderToMonitor != null && _folderToMonitor.length() > 0)
        {
            File folder = new File(_folderToMonitor);

            if (folder.isDirectory())
            {
                RecursiveFileMonitor rfm = new RecursiveFileMonitor();
                if (rfm.check(_folderToMonitor))
                {
                    Queue qu = Queue.getInstance();
                    qu.schedule((AbstractProject) _project);
                }
            }
        }
    }

    protected class RFMFilter implements FileFilter
    {
        public boolean accept(File pathname) {
            if (_excludedFilesExp.length() == 0)
                return true;
            else
                return !pathname.getName().matches(_excludedFilesExp);
        }
    }

    protected class RecursiveFileMonitor {
        private HashSet<String> _checkedSet;

        public RecursiveFileMonitor()
        {
           _checkedSet = new HashSet<String>();
        }

        protected boolean check(String path)
        {
            boolean modified = checkFilesInFolder(path);

            Set<String> prevFiles = ((HashMap<String, Long>)_fileMap.clone()).keySet();
            prevFiles.removeAll((Set)_checkedSet);

            for(String filePath : prevFiles)
            {
                // deleted file
                _fileMap.remove(filePath);
                modified = true;
            }
            
            return modified;
        }

        private boolean checkFilesInFolder(String path)
        {
            File filesArr[] = new File(path).listFiles(new RFMFilter());
            boolean modified = false;

            for (File file : filesArr)
            {
                String filePath = file.getPath();

                if (file.isDirectory())
                {
                    if (_recursiveCheck)
                        modified = checkFilesInFolder(filePath) || modified;
                }
                else
                {
                    Long prevModificationDate = _fileMap.get(filePath);
                    _checkedSet.add(filePath);

                    if (prevModificationDate == null)
                    {
                        // new file
                        _fileMap.put(filePath, file.lastModified());
                        modified = true;
                    }
                    else if (prevModificationDate != file.lastModified())
                    {
                        // modified file
                        _fileMap.put(filePath, file.lastModified());
                        modified = true;
                    }
                }
            }

            return modified;
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public void start(Item project, boolean newInstance) {
        super.start(project, newInstance);
        _project = project;
        _fileMap = new HashMap<String, Long>();
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor
    {
        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Folder Changed Trigger";
        }

    }
}
