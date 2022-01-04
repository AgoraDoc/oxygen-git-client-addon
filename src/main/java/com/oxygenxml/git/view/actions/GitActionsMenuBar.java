package com.oxygenxml.git.view.actions;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import com.oxygenxml.git.constants.Icons;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;
import com.oxygenxml.git.view.refresh.IRefreshable;

import ro.sync.exml.workspace.api.standalone.MenuBarCustomizer;
import ro.sync.exml.workspace.api.standalone.ui.OxygenUIComponentsFactory;

/**
 * Menu bar for Git.
 * 
 * @author Alex_Smarandache
 */
public class GitActionsMenuBar implements MenuBarCustomizer, IRefreshable {

	 /**
   * Helper class for singleton pattern.
   * 
   * @author alex_smarandache
   */
  private static class SingletonHelper {

    /**
     * The unique instance of Git menu bae.
     */
    static final GitActionsMenuBar INSTANCE = new GitActionsMenuBar();
  }
  
  /**
   * The translator for translations.
   */
  private static final Translator TRANSLATOR = Translator.getInstance();
  
	/**
	 * The Git menu.
	 */
	private final JMenu gitMenu = OxygenUIComponentsFactory.createMenu(TRANSLATOR.getTranslation(Tags.GIT));

	/**
	 * The git actions manager.
	 */
	private GitActionsManager gitActionsManager;

	/**
	 * The pull menu item.
	 */
	private JMenu pullMenuItem;
	
	/**
	 * Settings menu. Used in tests
	 */
	private JMenu settingsMenu;

	/**
	 * Private constructor to avoid instantiation.
	 */
	private GitActionsMenuBar() {
	}

	/**
	 * @return The singleton instance.
	 */
	public static GitActionsMenuBar getInstance() {
		return SingletonHelper.INSTANCE;
	}

	/**
	 * Populate menu with actions from git actions manager. 
	 * <br><br>
	 * This method will remove any previous component from this menu.
	 * 
	 * @param actionsManager The manager for git actions.
	 */
	public void populateMenu(final GitActionsManager actionsManager) {

		actionsManager.addRefreshable(this);
		this.gitActionsManager = actionsManager;

		// Add clone repository item
		final JMenuItem cloneRepositoryMenuItem = OxygenUIComponentsFactory.createMenuItem(actionsManager.getCloneRepositoryAction());
		cloneRepositoryMenuItem.setIcon(Icons.getIcon(Icons.GIT_CLONE_REPOSITORY_ICON));
		gitMenu.add(cloneRepositoryMenuItem);

		// Add open repository action.
		gitMenu.add(actionsManager.getOpenRepositoryAction());

		// Add push menu item
		final JMenuItem pushMenuItem = OxygenUIComponentsFactory.createMenuItem(actionsManager.getPushAction());
		actionsManager.getPushAction().setEnabled(false);
		pushMenuItem.setIcon(Icons.getIcon(Icons.GIT_PUSH_ICON));
		
		gitMenu.add(pushMenuItem);

		// Add pull options
		pullMenuItem = OxygenUIComponentsFactory.createMenu(TRANSLATOR.getTranslation(Tags.PULL));
		pullMenuItem.setIcon(Icons.getIcon(Icons.GIT_PULL_ICON));
		pullMenuItem.add(OxygenUIComponentsFactory.createMenuItem(actionsManager.getPullMergeAction()));
		pullMenuItem.add(OxygenUIComponentsFactory.createMenuItem(actionsManager.getPullRebaseAction()));
		pullMenuItem.setEnabled(
		    actionsManager.getPullMergeAction().isEnabled()|| actionsManager.getPullRebaseAction().isEnabled());

		gitMenu.add(pullMenuItem);

		// Add show staging item
		gitMenu.addSeparator();
		final JMenuItem showStagingMenuItem = OxygenUIComponentsFactory.createMenuItem(actionsManager.getShowStagingAction());
		showStagingMenuItem.setIcon(Icons.getIcon(ro.sync.ui.Icons.GIT_PLUGIN_ICON_MENU));
		gitMenu.add(showStagingMenuItem);

		// Add show branches item
		final JMenuItem showBranchesMenuItem = OxygenUIComponentsFactory.createMenuItem(actionsManager.getShowBranchesAction());
		showBranchesMenuItem.setIcon(Icons.getIcon(Icons.GIT_BRANCH_ICON));
		gitMenu.add(showBranchesMenuItem);

		// Add show tags item
		final JMenuItem showTagsMenuItem = OxygenUIComponentsFactory.createMenuItem(actionsManager.getShowTagsAction());
		showTagsMenuItem.setIcon(Icons.getIcon(Icons.TAG));
		gitMenu.add(showTagsMenuItem);

		// Add show history item
		final JMenuItem showHistoryMenuItem = OxygenUIComponentsFactory.createMenuItem(
				actionsManager.getShowHistoryAction());
		showHistoryMenuItem.setIcon(Icons.getIcon(Icons.GIT_HISTORY));
		gitMenu.add(showHistoryMenuItem);

		// Add submodules item
		final JMenuItem submodulesMenuItem = OxygenUIComponentsFactory.createMenuItem(actionsManager.getSubmoduleAction());
		submodulesMenuItem.setIcon(Icons.getIcon(Icons.GIT_SUBMODULE_ICON));
		gitMenu.add(submodulesMenuItem);

		// Add stash actions
		gitMenu.addSeparator();	
		final JMenuItem stashChangesMenuItem = OxygenUIComponentsFactory.createMenuItem(actionsManager.getStashChangesAction());
		stashChangesMenuItem.setIcon(Icons.getIcon(Icons.STASH_ICON));
		gitMenu.add(stashChangesMenuItem);
		gitMenu.add(actionsManager.getListStashesAction());

		// Add remote actions
		gitMenu.addSeparator();
		final JMenuItem manageRemoteMenuItem = OxygenUIComponentsFactory.createMenuItem(actionsManager.getManageRemoteRepositoriesAction());
		manageRemoteMenuItem.setIcon(Icons.getIcon(Icons.REMOTE));
		gitMenu.add(manageRemoteMenuItem);
		gitMenu.add(actionsManager.getTrackRemoteBranchAction());
		gitMenu.add(actionsManager.getEditConfigAction());

		// Add settings actions
		gitMenu.addSeparator();
		settingsMenu = OxygenUIComponentsFactory.createMenu(TRANSLATOR.getTranslation(Tags.SETTINGS));
		settingsMenu.setIcon(Icons.getIcon(Icons.SETTINGS));
		settingsMenu.add(OxygenUIComponentsFactory.createMenuItem(actionsManager.getResetAllCredentialsAction()));
		settingsMenu.add(OxygenUIComponentsFactory.createMenuItem(actionsManager.getOpenPreferencesAction()));

		gitMenu.add(settingsMenu);
	}


	/**
	 * Add git menu after "Tools" menu.
	 */
	@Override
	public void customizeMainMenu(JMenuBar mainMenu) {
		for (int i = 0; i < mainMenu.getMenuCount(); i++) {
			if (TRANSLATOR.getTranslation(Tags.TOOLS).equals(mainMenu.getMenu(i).getText())) {
				mainMenu.add(gitMenu, i + 1);
				break;
			}
		}
	}

	@Override
	public void refresh() {
		if(gitActionsManager != null) {
			pullMenuItem.setEnabled(gitActionsManager.getPullMergeAction().isEnabled() 
					|| gitActionsManager.getPullRebaseAction().isEnabled());
		}
	}

	/**
	 * !!! Used for tests. !!!
	 * 
	 * @return The settings menu.
	 */
	public JMenu getSettingsMenu() {
		return settingsMenu;
	}

}
