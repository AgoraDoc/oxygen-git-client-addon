package com.oxygenxml.git.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.errors.NoMergeBaseException.MergeBaseFailureReason;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.NullOutputStream;

import com.oxygenxml.git.WorkspaceAccessPlugin;
import com.oxygenxml.git.service.entities.FileStatus;
import com.oxygenxml.git.service.entities.GitChangeType;
import com.oxygenxml.git.utils.FileHelper;
import com.oxygenxml.git.utils.OptionsManager;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;

/**
 * Implements some basic git functionality like commit, push, pull, retrieve
 * File status(staged, unstaged)
 * 
 * TODO Beni Add monitors and progress.
 * 
 * @author intern2
 *
 */
public class GitAccess {

	private Git git;
	private static GitAccess instance;

	private GitAccess() {

	}

	public static GitAccess getInstance() {
		if (instance == null) {
			instance = new GitAccess();
		}
		return instance;
	}

	/**
	 * Sets the git repository. File path must exist
	 * 
	 * @param path
	 *          - A string that specifies the git Repository folder
	 */
	public void setRepository(String path) {

		try {
			git = Git.open(new File(path + "/.git"));
			Config config = git.getRepository().getConfig();
			config.setString("diff", null, "tool", "oxygendiff");
			config.setString("merge", null, "tool", "oxygendiff");
			config.setString("difftool", "oxygendiff", "cmd",
					"'[pathToOxygenInstallDir]/diffFiles.exe' -ext $REMOTE $LOCAL $LOCAL");
			config.setString("mergetool", "oxygendiff", "cmd",
					"'[pathToOxygenInstallDir]/diffFiles.exe' -ext $LOCAL $REMOTE $BASE $MERGED");
			config.setString("mergetool", "oxygendiff", "trustExitCode", "true");
			config.setString("difftool", null, "prompt", "false");
			git.getRepository().getConfig().save();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * 
	 * @return the git Repository
	 */
	public Repository getRepository() {
		return git.getRepository();
	}

	/**
	 * Creates a blank new Repository.
	 * 
	 * @param path
	 *          - A string that specifies the git Repository folder
	 */
	public void createNewRepository(String path) {

		try {
			git = Git.init().setDirectory(new File(path)).call();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Makes a diff between the files from the last commit and the files from the
	 * working directory. If there are diffs, they will be saved and returned.
	 * 
	 * @return - A list with all unstaged files
	 */
	public List<FileStatus> getUnstagedFiles() {
		List<FileStatus> unstagedFiles = new ArrayList<FileStatus>();
		List<FileStatus> stagedFiles = getStagedFile();
		List<FileStatus> conflictingFiles = getConflictingFiles();
		
		List<String> conflictingPaths = new ArrayList<String>();
		for (FileStatus conflictingFile : conflictingFiles) {
			conflictingPaths.add(conflictingFile.getFileLocation());
		}

		// needed only to pass it to the formatter
		OutputStream out = NullOutputStream.INSTANCE;

		DiffFormatter formatter = new DiffFormatter(out);
		formatter.setRepository(git.getRepository());
		try {
			AbstractTreeIterator commitTreeIterator = getLastCommitTreeIterator(git.getRepository(), Constants.HEAD);
			if (commitTreeIterator != null) {
				FileTreeIterator workTreeIterator = new FileTreeIterator(git.getRepository());
				List<DiffEntry> diffEntries = formatter.scan(commitTreeIterator, workTreeIterator);
				for (DiffEntry entry : diffEntries) {
					GitChangeType changeType = null;
					if(entry.getChangeType() == ChangeType.ADD){
						changeType = GitChangeType.ADD;
					} else if(entry.getChangeType() == ChangeType.MODIFY){
						changeType = GitChangeType.MODIFY;
					} else if(entry.getChangeType() == ChangeType.DELETE){
						changeType = GitChangeType.DELETE;
					}

					if (entry.getChangeType().equals(ChangeType.ADD) || entry.getChangeType().equals(ChangeType.COPY)
							|| entry.getChangeType().equals(ChangeType.RENAME)) {
						String filePath = entry.getNewPath();
						FileStatus unstageFile = new FileStatus(changeType, filePath);
						if (!stagedFiles.contains(unstageFile)) {
							if(!conflictingPaths.contains(filePath)){
								unstagedFiles.add(unstageFile);
							}
						}
					} else {
						String filePath = entry.getOldPath();
						FileStatus unstageFile = new FileStatus(changeType, filePath);
						if (!stagedFiles.contains(unstageFile)) {
							if(!conflictingPaths.contains(filePath)){
								unstagedFiles.add(unstageFile);
							}
						}
					}
				}
				unstagedFiles.addAll(conflictingFiles);
			} else {
				String selectedRepository = OptionsManager.getInstance().getSelectedRepository();
				List<String> fileNames = FileHelper.search(selectedRepository);
				for (String fileName : fileNames) {
					selectedRepository = selectedRepository.replace("\\", "/");
					int cut = selectedRepository.substring(selectedRepository.lastIndexOf("/") + 1).length();
					String file = fileName.substring(cut + 1);
					FileStatus unstageFile = new FileStatus(GitChangeType.ADD, file);
					unstagedFiles.add(unstageFile);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		formatter.close();

		return unstagedFiles;

	}

	/**
	 * Commits a single file locally
	 * 
	 * @param file
	 *          - File to be commited
	 * @param message
	 *          - Message for the commit
	 */
	public void commit(String message) {
		try {
			git.commit().setMessage(message).call();
		} catch (NoFilepatternException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}

	private AbstractTreeIterator getLastCommitTreeIterator(Repository repository, String branch) throws Exception {
		Ref head = repository.exactRef(branch);

		if (head.getObjectId() != null) {
			RevWalk walk = new RevWalk(repository);
			RevCommit commit = walk.parseCommit(head.getObjectId());
			RevTree tree = walk.parseTree(commit.getTree().getId());

			CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
			ObjectReader oldReader = repository.newObjectReader();
			oldTreeParser.reset(oldReader, tree.getId());
			walk.close();
			return oldTreeParser;
		} else {
			return null;
		}
	}

	/**
	 * Frees resources associated with the git instance.
	 */
	public void close() {
		git.close();

	}

	/**
	 * Gets all branches
	 * 
	 * @return - All the branches from the repository
	 */
	public List<Ref> getBrachList() {
		List<Ref> branches = null;
		try {
			branches = git.branchList().call();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
		return branches;
	}

	/**
	 * Creates a new branch in the repository
	 * 
	 * @param branchName
	 *          - Name for the new branch
	 */
	public void createBranch(String branchName) {
		try {
			git.branchCreate().setName(branchName).call();
		} catch (RefAlreadyExistsException e) {
			e.printStackTrace();
		} catch (RefNotFoundException e) {
			e.printStackTrace();
		} catch (InvalidRefNameException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Delete a branch from the repository
	 * 
	 * @param branchName
	 *          - Name for the branch to delete
	 */
	public void deleteBranch(String branchName) {
		try {
			git.branchDelete().setBranchNames(branchName).call();
		} catch (NotMergedException e) {
			e.printStackTrace();
		} catch (CannotDeleteCurrentBranchException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Pushes all the commits from the local repository to the remote repository
	 * 
	 * @param username
	 *          - Git username
	 * @param password
	 *          - Git password
	 * @throws GitAPIException
	 * @throws TransportException
	 * @throws InvalidRemoteException
	 */
	public void push(String username, String password)
			throws InvalidRemoteException, TransportException, GitAPIException {

		Iterable<PushResult> call = git.push()
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password)).call();
		Iterator<PushResult> results = call.iterator();
		while (results.hasNext()) {
			System.out.println(results.hasNext());
			PushResult result = results.next();
			for (RemoteRefUpdate info : result.getRemoteUpdates()) {
				System.out.println(info.getStatus());
			}
		}
		System.out.println(results.hasNext());
	}

	/**
	 * Pulls the files that are not on the local repository from the remote
	 * repository
	 * 
	 * @param username
	 *          - Git username
	 * @param password
	 *          - Git password
	 * @return 
	 * @throws GitAPIException
	 * @throws TransportException
	 * @throws NoHeadException
	 * @throws RefNotAdvertisedException
	 * @throws RefNotFoundException
	 * @throws CanceledException
	 * @throws InvalidRemoteException
	 * @throws DetachedHeadException
	 * @throws InvalidConfigurationException
	 * @throws WrongRepositoryStateException
	 * @throws IOException
	 * @throws IncorrectObjectTypeException
	 * @throws AmbiguousObjectException
	 * @throws RevisionSyntaxException
	 */
	public Set<String> pull(String username, String password) throws WrongRepositoryStateException,
			InvalidConfigurationException, DetachedHeadException, InvalidRemoteException, CanceledException,
			RefNotFoundException, RefNotAdvertisedException, NoHeadException, TransportException, GitAPIException,
			RevisionSyntaxException, AmbiguousObjectException, IncorrectObjectTypeException, IOException {

		PullResult result = git.pull().setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
				.call();
		System.out.println(result.toString());

		return result.getMergeResult().getConflicts().keySet();

	}

	private RevCommit getCommonAncestor(RevWalk walk, RevCommit a, RevCommit b)
			throws IncorrectObjectTypeException, IOException {
		walk.reset();
		walk.setRevFilter(RevFilter.MERGE_BASE);
		walk.markStart(a);
		walk.markStart(b);
		final RevCommit base = walk.next();
		if (base == null)
			return null;
		final RevCommit base2 = walk.next();
		if (base2 != null) {
			throw new NoMergeBaseException(MergeBaseFailureReason.MULTIPLE_MERGE_BASES_NOT_SUPPORTED,
					MessageFormat.format(JGitText.get().multipleMergeBasesFor, a.name(), b.name(), base.name(), base2.name()));
		}
		return base;
	}

	/**
	 * Adds a single file to the staging area. Preparing it for commit
	 * 
	 * @param file
	 *          - the name of the file to be added
	 */
	public void add(FileStatus file) {
		try {
			if (file.getChangeType().equals("DELETE")) {
				git.rm().addFilepattern(file.getFileLocation()).call();
			} else {
				git.add().addFilepattern(file.getFileLocation()).call();
			}
		} catch (NoFilepatternException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Adds multiple files to the staging area. Preparing the for commit
	 * 
	 * @param fileNames
	 *          - the names of the files to be added
	 */
	public void addAll(List<FileStatus> files) {
		try {
			for (FileStatus file : files) {
				if (file.getChangeType() == GitChangeType.DELETE) {
					git.rm().addFilepattern(file.getFileLocation()).call();
				} else {
					git.add().addFilepattern(file.getFileLocation()).call();
				}
			}
		} catch (NoFilepatternException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Well show all the files that have been added(also called staged) and are
	 * ready to be commited.
	 * 
	 * @return - a set containing all the staged file names
	 */
	public List<FileStatus> getStagedFile() {
		try {
			Status status = git.status().call();
			List<FileStatus> stagedFiles = new ArrayList<FileStatus>();
			for (String fileName : status.getChanged()) {
				stagedFiles.add(new FileStatus(GitChangeType.MODIFY, fileName));
			}
			for (String fileName : status.getAdded()) {
				stagedFiles.add(new FileStatus(GitChangeType.ADD, fileName));
			}
			for (String fileName : status.getRemoved()) {
				stagedFiles.add(new FileStatus(GitChangeType.DELETE, fileName));
			}
			return stagedFiles;
		} catch (NoWorkTreeException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}

		return new ArrayList<FileStatus>();
	}

	public List<FileStatus> getConflictingFiles() {
		try {
			Status status = git.status().call();
			List<FileStatus> stagedFiles = new ArrayList<FileStatus>();
			for (String fileName : status.getConflicting()) {
				stagedFiles.add(new FileStatus(GitChangeType.CONFLICT, fileName));
			}
			
			return stagedFiles;
		} catch (NoWorkTreeException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}

		return new ArrayList<FileStatus>();
	}
	
	
	/**
	 * Removes a single file from the staging area.
	 * 
	 * @param fileName
	 *          - the file to be removed from the staging area
	 */
	public void remove(FileStatus file) {
		try {
			ResetCommand reset = git.reset();
			reset.addPath(file.getFileLocation());
			reset.call();
		} catch (NoFilepatternException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Removes all the specified files from the staging area.
	 * 
	 * @param fileNames
	 *          - the list of file to be removed
	 */
	public void removeAll(List<FileStatus> files) {
		try {
			ResetCommand reset = git.reset();
			for (FileStatus file : files) {
				reset.addPath(file.getFileLocation());

			}
			reset.call();
		} catch (NoFilepatternException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Gets the host name from the repositoryURL
	 * 
	 * @return the host name
	 */
	public String getHostName() {
		Config storedConfig = git.getRepository().getConfig();
		String url = storedConfig.getString("remote", "origin", "url");
		try {
			URL u = new URL(url);
			url = u.getHost();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		return url;
	}

	public URL getFileContent(String path) {
		// find the HEAD

		String selectedRepository = OptionsManager.getInstance().getSelectedRepository();
		selectedRepository = selectedRepository.replace("\\", "/");
		URL url = null;
		File file = new File(selectedRepository + "/" + path);
		try {
			url = file.toURI().toURL();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return url;

	}

	public ObjectId getLastLocalCommit() {
		Repository repo = git.getRepository();
		try {
			ObjectId localCommit = repo.resolve("HEAD^{commit}");
			return localCommit;
		} catch (RevisionSyntaxException e) {
			e.printStackTrace();
		} catch (AmbiguousObjectException e) {
			e.printStackTrace();
		} catch (IncorrectObjectTypeException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public ObjectId getRemoteCommit() {
		Repository repo = git.getRepository();
		try {
			ObjectId remoteCommit = repo.resolve("FETCH_HEAD^{commit}");
			return remoteCommit;
		} catch (RevisionSyntaxException e) {
			e.printStackTrace();
		} catch (AmbiguousObjectException e) {
			e.printStackTrace();
		} catch (IncorrectObjectTypeException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public ObjectId getBaseCommit() {
		Repository repository = git.getRepository();
		RevWalk walk = new RevWalk(repository);
		ObjectId localCommit;
		ObjectId remoteCommit;
		RevCommit baseCommit = null;
		try {
			localCommit = repository.resolve("FETCH_HEAD^{commit}");
			remoteCommit = repository.resolve("HEAD^{commit}");
			baseCommit = getCommonAncestor(walk, walk.parseCommit(localCommit), walk.parseCommit(remoteCommit));
		} catch (RevisionSyntaxException e) {
			e.printStackTrace();
		} catch (AmbiguousObjectException e) {
			e.printStackTrace();
		} catch (IncorrectObjectTypeException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		walk.close();
		return baseCommit.toObjectId();
	}

	public InputStream getInputStreamFrom(ObjectId commit, String path)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		Repository repository = git.getRepository();
		RevWalk revWalk = new RevWalk(repository);
		RevCommit revCommit = revWalk.parseCommit(commit);
		// and using commit's tree find the path
		RevTree tree = revCommit.getTree();
		TreeWalk treeWalk = new TreeWalk(repository);
		treeWalk.addTree(tree);
		treeWalk.setRecursive(true);
		treeWalk.setFilter(PathFilter.create(path));

		ObjectLoader loader = null;
		if (treeWalk.next()) {
			ObjectId objectId = treeWalk.getObjectId(0);
			loader = repository.open(objectId);
		}

		treeWalk.close();
		revWalk.close();
		
		return loader.openStream();
	}

	public void reset(){
		try {
			git.reset().call();
		} catch (CheckoutConflictException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}
}
