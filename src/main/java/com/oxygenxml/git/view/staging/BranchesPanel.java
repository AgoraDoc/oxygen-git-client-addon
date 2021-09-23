package com.oxygenxml.git.view.staging;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.apache.log4j.Logger;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;

import com.oxygenxml.git.constants.UIConstants;
import com.oxygenxml.git.service.BranchInfo;
import com.oxygenxml.git.service.GitAccess;
import com.oxygenxml.git.service.GitEventAdapter;
import com.oxygenxml.git.service.GitOperationScheduler;
import com.oxygenxml.git.service.NoRepositorySelected;
import com.oxygenxml.git.service.RepoNotInitializedException;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;
import com.oxygenxml.git.utils.RepoUtil;
import com.oxygenxml.git.view.branches.BranchesUtil;
import com.oxygenxml.git.view.dialog.BranchSwitchConfirmationDialog;
import com.oxygenxml.git.view.dialog.OKOtherAndCancelDialog;
import com.oxygenxml.git.view.event.GitController;
import com.oxygenxml.git.view.event.GitEventInfo;
import com.oxygenxml.git.view.event.GitOperation;
import com.oxygenxml.git.view.stash.StashUtil;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;

/**
 * Panel in Git Staging view from where to change the current branch.
 */
public class BranchesPanel extends JPanel {
  
  
  /**
   * Logger for logging.
   */
  private static final Logger LOGGER = Logger.getLogger(BranchesPanel.class);

  /**
   * i18n
   */
  private static final Translator TRANSLATOR = Translator.getInstance();
  
  /**
   * Access to the Git API.
   */
  private static final GitAccess GIT_ACCESS = GitAccess.getInstance();

  /**
   * Branch names combo.
   */
  private JComboBox<String> branchNamesCombo;
  
  /**
   * <code>true</code> if the combo popup is showing.
   */
  private boolean isComboPopupShowing;
  
  /**
   * <code>true</code> to inhibit branch selection listener.
   */
  private boolean inhibitBranchSelectionListener;
  
  /**
   * The ID of the commit on which a detached HEAD is set.
   */
  private String detachedHeadId;
  
  private final boolean isLabeled;
  
  /**
   * Creates the panel.
   * 
   * @param gitController Git Controller.
   */
  public BranchesPanel(GitController gitController, boolean isLabeled) {
    this.isLabeled = isLabeled;
    
    createGUI();
    
    branchNamesCombo.addItemListener(event -> {
      if (!inhibitBranchSelectionListener && event.getStateChange() == ItemEvent.SELECTED) {
        treatBranchSelectedEvent(event);
      }
    });
    
    branchNamesCombo.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        isComboPopupShowing = true;
      }
      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        isComboPopupShowing = false;
      }
      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        isComboPopupShowing = false;
      }
    });
    
    gitController.addGitListener(new GitEventAdapter() {
      @Override
      public void operationSuccessfullyEnded(GitEventInfo info) {
        GitOperation operation = info.getGitOperation();
        if (operation == GitOperation.OPEN_WORKING_COPY
            || operation == GitOperation.ABORT_REBASE 
            || operation == GitOperation.CONTINUE_REBASE
            || operation == GitOperation.COMMIT) {
          refresh();
        }
      }
    });
    
  }

  /**
   * Treat a branch name selection event.
   * 
   * @param event The event to treat.
   */
  private void treatBranchSelectedEvent(ItemEvent event) {
    String branchName = (String) event.getItem();
    BranchInfo currentBranchInfo = GIT_ACCESS.getBranchInfo();
    String currentBranchName = currentBranchInfo.getBranchName();
    if (branchName.equals(currentBranchName)) {
      return;
    }
    
    RepositoryState repoState = RepoUtil.getRepoState().orElse(null);
    if(RepoUtil.isNonConflictualRepoWithUncommittedChanges(repoState)) {
      BranchSwitchConfirmationDialog dialog = new BranchSwitchConfirmationDialog(branchName);

      dialog.setVisible(true);

      int answer = dialog.getResult();

      if(answer == OKOtherAndCancelDialog.RESULT_OTHER) {
        tryCheckingOutBranch(currentBranchInfo, branchName);
      } else if(answer == OKOtherAndCancelDialog.RESULT_OK) {
        boolean wasStashCreated = StashUtil.stashChanges();
        if(wasStashCreated) {
          tryCheckingOutBranch(currentBranchInfo, branchName);
        }
      } else {
        restoreCurrentBranchSelectionInMenu();
      }
    } else {
      tryCheckingOutBranch(currentBranchInfo, branchName);
    }
  }

  /**
   * Create the graphical user interface.
   */
  private void createGUI() {
    setLayout(new GridBagLayout());
    
    // Branch label
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.insets = new Insets(
        UIConstants.COMPONENT_TOP_PADDING,
        UIConstants.COMPONENT_LEFT_PADDING,
        UIConstants.COMPONENT_BOTTOM_PADDING,
        UIConstants.COMPONENT_RIGHT_PADDING);
    if(isLabeled) {
      JLabel currentBranchLabel = new JLabel(Translator.getInstance().getTranslation(Tags.BRANCH) + ":");
      add(currentBranchLabel, gbc);
    }
    
    // Branches combo
    branchNamesCombo = new JComboBox<>();
    gbc.gridx = isLabeled ? 1 : 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1;
    add(branchNamesCombo, gbc);
  }
  
  /**
   * Refresh.
   */
  public void refresh() {
    int pullsBehind = GIT_ACCESS.getPullsBehind();
    int pushesAhead = -1;
    try {
      pushesAhead = GIT_ACCESS.getPushesAhead();
    } catch (RepoNotInitializedException e) {
      LOGGER.debug(e, e);
    }
    
    SwingUtilities.invokeLater(this::updateBranchesPopup);
    
    Repository repo = null;
    try {
      repo = GIT_ACCESS.getRepository();
    } catch (NoRepositorySelected e) {
      LOGGER.debug(e, e);
    }

    BranchInfo branchInfo = GIT_ACCESS.getBranchInfo();
    String currentBranchName = branchInfo.getBranchName();
    if (branchInfo.isDetached()) {
      detachedHeadId = currentBranchName;
      
      String tooltipText = "<html>"
          + TRANSLATOR.getTranslation(Tags.TOOLBAR_PANEL_INFORMATION_STATUS_DETACHED_HEAD)
          + " "
          + currentBranchName;
      if (repo != null && repo.getRepositoryState() == RepositoryState.REBASING_MERGE) {
        tooltipText += "<br>" + TRANSLATOR.getTranslation(Tags.REBASE_IN_PROGRESS) + ".";
      }
      tooltipText += "</html>";
      String finalText = tooltipText;
      SwingUtilities.invokeLater(() -> branchNamesCombo.setToolTipText(finalText));
    } else {
      detachedHeadId = null;
      
      String branchTooltip = null;
      if (currentBranchName != null && !currentBranchName.isEmpty()) {

        String upstreamBranchFromConfig = GIT_ACCESS.getUpstreamBranchShortNameFromConfig(currentBranchName);
        boolean isAnUpstreamBranchDefinedInConfig = upstreamBranchFromConfig != null;

        String upstreamShortestName = 
            isAnUpstreamBranchDefinedInConfig 
            ? upstreamBranchFromConfig.substring(upstreamBranchFromConfig.lastIndexOf('/') + 1)
                : null;
        Ref remoteBranchRefForUpstreamFromConfig = 
            isAnUpstreamBranchDefinedInConfig 
            ? RepoUtil.getRemoteBranch(upstreamShortestName) 
                : null;
        boolean existsRemoteBranchForUpstreamDefinedInConfig = remoteBranchRefForUpstreamFromConfig != null;

        branchTooltip = "<html>"
            + TRANSLATOR.getTranslation(Tags.LOCAL_BRANCH)
            + " <b>" + currentBranchName + "</b>.<br>"
            + TRANSLATOR.getTranslation(Tags.UPSTREAM_BRANCH)
            + " <b>" 
            + (isAnUpstreamBranchDefinedInConfig && existsRemoteBranchForUpstreamDefinedInConfig 
                ? upstreamBranchFromConfig 
                    : TRANSLATOR.getTranslation(Tags.NO_UPSTREAM_BRANCH))
            + "</b>.<br>";

        String commitsBehindMessage = "";
        String commitsAheadMessage = "";
        if (isAnUpstreamBranchDefinedInConfig && existsRemoteBranchForUpstreamDefinedInConfig) {
          if (pullsBehind == 0) {
            commitsBehindMessage = TRANSLATOR.getTranslation(Tags.TOOLBAR_PANEL_INFORMATION_STATUS_UP_TO_DATE);
          } else if (pullsBehind == 1) {
            commitsBehindMessage = TRANSLATOR.getTranslation(Tags.ONE_COMMIT_BEHIND);
          } else {
            commitsBehindMessage = MessageFormat.format(TRANSLATOR.getTranslation(Tags.COMMITS_BEHIND), pullsBehind);
          }
          branchTooltip += commitsBehindMessage + "<br>";

          if (pushesAhead == 0) {
            commitsAheadMessage = TRANSLATOR.getTranslation(Tags.NOTHING_TO_PUSH);
          } else if (pushesAhead == 1) {
            commitsAheadMessage = TRANSLATOR.getTranslation(Tags.ONE_COMMIT_AHEAD);
          } else {
            commitsAheadMessage = MessageFormat.format(TRANSLATOR.getTranslation(Tags.COMMITS_AHEAD), pushesAhead);
          }
          branchTooltip += commitsAheadMessage;
        }

        branchTooltip += "</html>";
      }
      String branchTooltipFinal = branchTooltip;
      SwingUtilities.invokeLater(() -> branchNamesCombo.setToolTipText(branchTooltipFinal));
    }
  }
  
  /**
   * Adds the branches given as a parameter to the branchSplitMenuButton.
   * 
   * @param branches A list with the branches to be added.
   */
  private void addBranchesToCombo(List<String> branches) {
    inhibitBranchSelectionListener = true;
    branches.forEach(branchNamesCombo::addItem);
    inhibitBranchSelectionListener = false;
    
    if (detachedHeadId != null) {
      branchNamesCombo.addItem(detachedHeadId);
    }
    
    String currentBranchName = GIT_ACCESS.getBranchInfo().getBranchName();
    branchNamesCombo.setSelectedItem(currentBranchName);
  }
  
  /**
   * Updates the local branches in the combo popup.
   */
  private void updateBranchesPopup() {
    boolean isVisible = isComboPopupShowing;
    branchNamesCombo.hidePopup();

    branchNamesCombo.removeAllItems();
    addBranchesToCombo(getBranches());

    branchNamesCombo.revalidate();
    if (isVisible) {
      branchNamesCombo.showPopup();
    }
  }
  
  /**
   * Gets all the local branches from the current repository.
   * 
   * @return The list of local branches.
   */
  private List<String> getBranches() {
    List<String> localBranches = new ArrayList<>();
    try {
      localBranches = BranchesUtil.getLocalBranches();
    } catch (NoRepositorySelected e1) {
      PluginWorkspaceProvider.getPluginWorkspace().showErrorMessage(e1.getMessage(), e1);
    }
    return localBranches;
  }
  
  /**
   * The action performed for this Abstract Action
   * 
   * @param oldBranchInfo Old branch info.
   * @param newBranchName New branch name.
   */
  private void tryCheckingOutBranch(BranchInfo oldBranchInfo, String newBranchName) {
    RepositoryState repoState = RepoUtil.getRepoState().orElse(null);
    if (oldBranchInfo.isDetached() && !RepoUtil.isRepoRebasing(repoState)) {
      detachedHeadId = null;
      branchNamesCombo.removeItem(oldBranchInfo.getBranchName());
    }
    
    GitOperationScheduler.getInstance().schedule(() -> {
      try {
        GIT_ACCESS.setBranch(newBranchName);
        BranchesUtil.fixupFetchInConfig(GIT_ACCESS.getRepository().getConfig());
      } catch (CheckoutConflictException ex) {
        restoreCurrentBranchSelectionInMenu();
        BranchesUtil.showBranchSwitchErrorMessage();
      } catch (GitAPIException | JGitInternalException | IOException | NoRepositorySelected ex) {
        restoreCurrentBranchSelectionInMenu();
        PluginWorkspaceProvider.getPluginWorkspace().showErrorMessage(ex.getMessage(), ex);
      }
    });
  }
  
  /**
   * Restore current branch selection in branches menu.
   */
  private void restoreCurrentBranchSelectionInMenu() {
    String currentBranchName = GIT_ACCESS.getBranchInfo().getBranchName();
    int itemCount = branchNamesCombo.getItemCount();
    for (int i = 0; i < itemCount; i++) {
      String branch = branchNamesCombo.getItemAt(i);
      if (branch.equals(currentBranchName)) {
        branchNamesCombo.setSelectedItem(branch);
        break;
      }
    }
  }
  
  /**
   * @return the branches combo.
   */
  public JComboBox<String> getBranchNamesCombo() {
    return branchNamesCombo;
  }
}
