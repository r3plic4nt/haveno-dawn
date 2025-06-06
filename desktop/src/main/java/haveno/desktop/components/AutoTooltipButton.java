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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.skins.JFXButtonSkin;
import javafx.scene.Node;
import javafx.scene.control.Skin;

import static haveno.desktop.components.TooltipUtil.showTooltipIfTruncated;

public class AutoTooltipButton extends JFXButton {

    public AutoTooltipButton() {
        super();
    }

    public AutoTooltipButton(String text) {
        super(text);
    }

    public AutoTooltipButton(String text, Node graphic) {
        super(text, graphic);
    }

    public void updateText(String text) {
        setText(text);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AutoTooltipButtonSkin(this);
    }

    private class AutoTooltipButtonSkin extends JFXButtonSkin {
        public AutoTooltipButtonSkin(JFXButton button) {
            super(button);
        }

        @Override
        protected void layoutChildren(double x, double y, double w, double h) {
            super.layoutChildren(x, y, w, h);
            showTooltipIfTruncated(this, getSkinnable());
        }
    }
}
