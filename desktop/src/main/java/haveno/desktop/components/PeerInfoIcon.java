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

import haveno.desktop.main.overlays.editor.PeerInfoWithTagEditor;
import haveno.desktop.util.DisplayUtils;

import haveno.core.alert.PrivateNotificationManager;
import haveno.core.locale.Res;
import haveno.core.offer.Offer;
import haveno.core.trade.Trade;
import haveno.core.user.Preferences;

import haveno.network.p2p.NodeAddress;

import com.google.common.base.Charsets;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import javafx.geometry.Point2D;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class PeerInfoIcon extends Group {

    protected Preferences preferences;
    protected final String fullAddress;
    protected String tooltipText;
    protected Label tagLabel;
    private Label numTradesLabel;
    protected Pane tagPane;
    protected Pane numTradesPane;
    protected int numTrades = 0;
    private final StringProperty tag;

    public PeerInfoIcon(NodeAddress nodeAddress, Preferences preferences) {
        this.preferences = preferences;
        this.fullAddress = nodeAddress != null ? nodeAddress.getFullAddress() : "";
        this.tag = new SimpleStringProperty("");
    }

    protected void createAvatar(Color ringColor) {
        double scaleFactor = getScaleFactor();
        double outerSize = 26 * scaleFactor;
        Canvas outerBackground = new Canvas(outerSize, outerSize);
        GraphicsContext outerBackgroundGc = outerBackground.getGraphicsContext2D();
        outerBackgroundGc.setFill(ringColor);
        outerBackgroundGc.fillOval(0, 0, outerSize, outerSize);
        outerBackground.setLayoutY(1 * scaleFactor);

        // inner circle
        int maxIndices = 15;
        int intValue = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] bytes = md.digest(fullAddress.getBytes(Charsets.UTF_8));
            intValue = Math.abs(((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                    | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF));

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            log.error(e.toString());
        }

        int index = (intValue % maxIndices) + 1;
        double saturation = (intValue % 1000) / 1000d;
        int red = (intValue >> 8) % 256;
        int green = (intValue >> 16) % 256;
        int blue = (intValue >> 24) % 256;

        Color innerColor = Color.rgb(red, green, blue);
        innerColor = innerColor.deriveColor(1, saturation, 0.8, 1); // reduce saturation and brightness

        double innerSize = scaleFactor * 22;
        Canvas innerBackground = new Canvas(innerSize, innerSize);
        GraphicsContext innerBackgroundGc = innerBackground.getGraphicsContext2D();
        innerBackgroundGc.setFill(innerColor);
        innerBackgroundGc.fillOval(0, 0, innerSize, innerSize);
        innerBackground.setLayoutY(3 * scaleFactor);
        innerBackground.setLayoutX(2 * scaleFactor);

        ImageView avatarImageView = new ImageView();
        avatarImageView.setId("avatar_" + index);
        avatarImageView.setLayoutX(0);
        avatarImageView.setLayoutY(1 * scaleFactor);
        avatarImageView.setFitHeight(scaleFactor * 26);
        avatarImageView.setFitWidth(scaleFactor * 26);

        numTradesPane = new Pane();
        numTradesPane.relocate(scaleFactor * 18, scaleFactor * 14);
        numTradesPane.setMouseTransparent(true);
        ImageView numTradesCircle = new ImageView();
        numTradesCircle.setId("image-green_circle_solid");

        numTradesLabel = new AutoTooltipLabel();
        numTradesLabel.relocate(scaleFactor * 5, scaleFactor * 1);
        numTradesLabel.setId("ident-num-label");
        numTradesPane.getChildren().addAll(numTradesCircle, numTradesLabel);

        tagPane = new Pane();
        tagPane.relocate(Math.round(scaleFactor * 18), scaleFactor * -2);
        tagPane.setMouseTransparent(true);
        ImageView tagCircle = new ImageView();
        tagCircle.setId("image-blue_circle_solid");
        tagLabel = new AutoTooltipLabel();
        tagLabel.relocate(Math.round(scaleFactor * 5), scaleFactor * 1);
        tagLabel.setId("ident-num-label");
        tagPane.getChildren().addAll(tagCircle, tagLabel);

        updatePeerInfoIcon();

        getChildren().addAll(outerBackground, innerBackground, avatarImageView, tagPane, numTradesPane);
    }

    protected void addMouseListener(int numTrades,
                                    PrivateNotificationManager privateNotificationManager,
                                    @Nullable Trade trade,
                                    Offer offer,
                                    Preferences preferences,
                                    boolean useDevPrivilegeKeys,
                                    boolean isFiatCurrency,
                                    long peersAccountAge,
                                    long peersSignAge,
                                    String peersAccountAgeInfo,
                                    String peersSignAgeInfo,
                                    String accountSigningState) {

        final String accountAgeFormatted = isFiatCurrency ?
                peersAccountAge > -1 ?
                        DisplayUtils.formatAccountAge(peersAccountAge) :
                        Res.get("peerInfo.unknownAge") :
                null;

        final String signAgeFormatted = isFiatCurrency && peersSignAgeInfo != null ?
                peersSignAge > -1 ?
                        DisplayUtils.formatAccountAge(peersSignAge) :
                        Res.get("peerInfo.unknownAge") :
                null;

        setOnMouseClicked(e -> {
            if (e.getButton().equals(MouseButton.PRIMARY)) {
                new PeerInfoWithTagEditor(privateNotificationManager, trade, offer, preferences, useDevPrivilegeKeys)
                        .fullAddress(fullAddress)
                        .numTrades(numTrades)
                        .accountAge(accountAgeFormatted)
                        .signAge(signAgeFormatted)
                        .accountAgeInfo(peersAccountAgeInfo)
                        .signAgeInfo(peersSignAgeInfo)
                        .accountSigningState(accountSigningState)
                        .position(localToScene(new Point2D(0, 0)))
                        .onSave(newTag -> {
                            preferences.setTagForPeer(fullAddress, newTag);
                            tag.set(newTag);
                        })
                        .show();
            }
        });
    }

    protected double getScaleFactor() {
        return 1;
    }

    protected String getAccountAgeTooltip(Long accountAge) {
        return accountAge > -1 ?
                Res.get("peerInfoIcon.tooltip.age", DisplayUtils.formatAccountAge(accountAge)) :
                Res.get("peerInfoIcon.tooltip.unknownAge");
    }

    protected void updatePeerInfoIcon() {
        if (numTrades > 0) {
            numTradesLabel.setText(numTrades > 99 ? "*" : String.valueOf(numTrades));

            double scaleFactor = getScaleFactor();
            if (numTrades > 9 && numTrades < 100) {
                numTradesLabel.relocate(scaleFactor * 2, scaleFactor * 1);
            } else {
                numTradesLabel.relocate(scaleFactor * 5, scaleFactor * 1);
            }
        }
        numTradesPane.setVisible(numTrades > 0);

        refreshTag();
    }

    protected void refreshTag() {
        Map<String, String> peerTagMap = preferences.getPeerTagMap();
        if (peerTagMap.containsKey(fullAddress)) {
            tag.set(peerTagMap.get(fullAddress));
        }

        Tooltip.install(this, new Tooltip(!tag.get().isEmpty() ?
                Res.get("peerInfoIcon.tooltip", tooltipText, tag.get()) : tooltipText));

        if (!tag.get().isEmpty()) {
            tagLabel.setText(tag.get().substring(0, 1));
        }
        tagPane.setVisible(!tag.get().isEmpty());
    }

    protected StringProperty tagProperty() {
        return tag;
    }
}
