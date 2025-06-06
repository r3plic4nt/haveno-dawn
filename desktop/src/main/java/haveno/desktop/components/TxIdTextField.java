/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package haveno.desktop.components;

import com.jfoenix.controls.JFXTextField;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import haveno.common.UserThread;
import haveno.common.util.Utilities;
import haveno.core.locale.Res;
import haveno.core.trade.Trade;
import haveno.core.user.BlockChainExplorer;
import haveno.core.user.Preferences;
import haveno.core.xmr.wallet.XmrWalletService;
import haveno.desktop.components.indicator.TxConfidenceIndicator;
import haveno.desktop.util.GUIUtil;
import haveno.desktop.util.Layout;
import javafx.beans.value.ChangeListener;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import lombok.Getter;
import lombok.Setter;
import monero.daemon.model.MoneroTx;
import monero.wallet.model.MoneroWalletListener;

import javax.annotation.Nullable;

public class TxIdTextField extends AnchorPane {
    @Setter
    private static Preferences preferences;
    @Setter
    private static XmrWalletService xmrWalletService;

    @Getter
    private final TextField textField;
    private final Tooltip progressIndicatorTooltip;
    private final TxConfidenceIndicator txConfidenceIndicator;
    private final Label copyLabel, blockExplorerIcon, missingTxWarningIcon;

    private MoneroWalletListener walletListener;
    private ChangeListener<Number> tradeListener;
    private Trade trade;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxIdTextField() {
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setFocusTraversable(false);
        txConfidenceIndicator.setMaxSize(20, 20);
        txConfidenceIndicator.setId("funds-confidence");
        txConfidenceIndicator.setLayoutY(1);
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setVisible(false);
        AnchorPane.setRightAnchor(txConfidenceIndicator, 0.0);
        AnchorPane.setTopAnchor(txConfidenceIndicator, Layout.FLOATING_ICON_Y);
        progressIndicatorTooltip = new Tooltip("-");
        txConfidenceIndicator.setTooltip(progressIndicatorTooltip);

        copyLabel = new Label();
        copyLabel.setLayoutY(Layout.FLOATING_ICON_Y);
        copyLabel.getStyleClass().addAll("icon", "highlight");
        copyLabel.setTooltip(new Tooltip(Res.get("txIdTextField.copyIcon.tooltip")));
        copyLabel.setGraphic(GUIUtil.getCopyIcon());
        copyLabel.setCursor(Cursor.HAND);
        AnchorPane.setRightAnchor(copyLabel, 30.0);

        Tooltip tooltip = new Tooltip(Res.get("txIdTextField.blockExplorerIcon.tooltip"));

        blockExplorerIcon = new Label();
        blockExplorerIcon.getStyleClass().addAll("icon", "highlight");
        blockExplorerIcon.setTooltip(tooltip);
        AwesomeDude.setIcon(blockExplorerIcon, AwesomeIcon.EXTERNAL_LINK);
        blockExplorerIcon.setMinWidth(20);
        AnchorPane.setRightAnchor(blockExplorerIcon, 52.0);
        AnchorPane.setTopAnchor(blockExplorerIcon, Layout.FLOATING_ICON_Y);

        missingTxWarningIcon = new Label();
        missingTxWarningIcon.getStyleClass().addAll("icon", "error-icon");
        AwesomeDude.setIcon(missingTxWarningIcon, AwesomeIcon.WARNING_SIGN);
        missingTxWarningIcon.setTooltip(new Tooltip(Res.get("txIdTextField.missingTx.warning.tooltip")));
        missingTxWarningIcon.setMinWidth(20);
        AnchorPane.setRightAnchor(missingTxWarningIcon, 52.0);
        AnchorPane.setTopAnchor(missingTxWarningIcon, Layout.FLOATING_ICON_Y);
        missingTxWarningIcon.setVisible(false);
        missingTxWarningIcon.setManaged(false);

        textField = new JFXTextField();
        textField.setId("address-text-field");
        textField.setEditable(false);
        textField.setTooltip(tooltip);
        AnchorPane.setRightAnchor(textField, 80.0);
        AnchorPane.setLeftAnchor(textField, 0.0);
        textField.focusTraversableProperty().set(focusTraversableProperty().get());
        getChildren().addAll(textField, missingTxWarningIcon, blockExplorerIcon, copyLabel, txConfidenceIndicator);
    }

    public void setup(@Nullable String txId) {
        setup(txId, null);
    }

    public void setup(@Nullable String txId, Trade trade) {
        this.trade = trade;
        if (walletListener != null) {
            xmrWalletService.removeWalletListener(walletListener);
            walletListener = null;
        }
        if (tradeListener != null) {
            trade.getDepositTxsUpdateCounter().removeListener(tradeListener);
            tradeListener = null;
        }

        if (txId == null) {
            textField.setText(Res.get("shared.na"));
            textField.setId("address-text-field-error");
            blockExplorerIcon.setVisible(false);
            blockExplorerIcon.setManaged(false);
            copyLabel.setVisible(false);
            copyLabel.setManaged(false);
            txConfidenceIndicator.setVisible(false);
            missingTxWarningIcon.setVisible(true);
            missingTxWarningIcon.setManaged(true);
            return;
        }

        // subscribe for tx updates
        if (trade == null) {
            walletListener = new MoneroWalletListener() {
                @Override
                public void onNewBlock(long height) {
                    updateConfidence(txId, trade, false, height);
                }
            };
            xmrWalletService.addWalletListener(walletListener); // TODO: this only listens for new blocks, listen for double spend
        } else {
            tradeListener = (observable, oldValue, newValue) -> {
                updateConfidence(txId, trade, null, null);
            };
            trade.getDepositTxsUpdateCounter().addListener(tradeListener);
        }

        textField.setText(txId);
        textField.setOnMouseClicked(mouseEvent -> openBlockExplorer(txId));
        blockExplorerIcon.setOnMouseClicked(mouseEvent -> openBlockExplorer(txId));
        copyLabel.setOnMouseClicked(e -> {
            Utilities.copyToClipboard(txId);
            Tooltip tp = new Tooltip(Res.get("shared.copiedToClipboard"));
            Node node = (Node) e.getSource();
            UserThread.runAfter(() -> tp.hide(), 1);
            tp.show(node, e.getScreenX() + Layout.PADDING, e.getScreenY() + Layout.PADDING);
        });
        txConfidenceIndicator.setVisible(true);

        // update off main thread
        new Thread(() -> updateConfidence(txId, trade, true, null)).start();
    }

    public void cleanup() {
        if (xmrWalletService != null && walletListener != null) {
            xmrWalletService.removeWalletListener(walletListener);
            walletListener = null;
        }
        if (tradeListener != null) {
            trade.getDepositTxsUpdateCounter().removeListener(tradeListener);
            tradeListener = null;
        }
        trade = null;
        textField.setOnMouseClicked(null);
        blockExplorerIcon.setOnMouseClicked(null);
        copyLabel.setOnMouseClicked(null);
        textField.setText("");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void openBlockExplorer(String txId) {
        if (preferences != null) {
            BlockChainExplorer blockChainExplorer = preferences.getBlockChainExplorer();
            GUIUtil.openWebPage(blockChainExplorer.txUrl + txId, false);
        }
    }

    private synchronized void updateConfidence(String txId, Trade trade, Boolean useCache, Long height) {
        MoneroTx tx = null;
        try {
            if (trade == null) {
                tx = useCache ? xmrWalletService.getDaemonTxWithCache(txId) : xmrWalletService.getDaemonTx(txId);
                tx.setNumConfirmations(tx.isConfirmed() ? (height == null ? xmrWalletService.getXmrConnectionService().getLastInfo().getHeight() : height) - tx.getHeight(): 0l); // TODO: don't set if tx.getNumConfirmations() works reliably on non-local testnet
            } else {
                if (txId.equals(trade.getMaker().getDepositTxHash())) tx = trade.getMakerDepositTx();
                else if (txId.equals(trade.getTaker().getDepositTxHash())) tx = trade.getTakerDepositTx();
            }
        } catch (Exception e) {
            // do nothing
        }
        updateConfidence(tx, trade);
    }

    private void updateConfidence(MoneroTx tx, Trade trade) {
        UserThread.execute(() -> {
            GUIUtil.updateConfidence(tx, trade, progressIndicatorTooltip, txConfidenceIndicator);
            if (txConfidenceIndicator.getProgress() != 0) {
                AnchorPane.setRightAnchor(txConfidenceIndicator, 0.0);
            }
            if (txConfidenceIndicator.getProgress() >= 1.0 && walletListener != null) {
                xmrWalletService.removeWalletListener(walletListener); // unregister listener
                walletListener = null;
            }
        });
    }
}
