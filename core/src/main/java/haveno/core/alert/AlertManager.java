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

 package haveno.core.alert;

 import com.google.common.base.Charsets;
 import com.google.inject.Inject;
 import com.google.inject.name.Named;
 import haveno.common.app.DevEnv;
 import haveno.common.config.Config;
 import haveno.common.crypto.KeyRing;
 import haveno.core.user.User;
 import haveno.network.p2p.P2PService;
 import haveno.network.p2p.storage.HashMapChangedListener;
 import haveno.network.p2p.storage.payload.ProtectedStorageEntry;
 import haveno.network.p2p.storage.payload.ProtectedStoragePayload;
 import java.math.BigInteger;
 import java.security.SignatureException;
 import java.util.Collection;
 import java.util.List;
 import javafx.beans.property.ObjectProperty;
 import javafx.beans.property.ReadOnlyObjectProperty;
 import javafx.beans.property.SimpleObjectProperty;
 import org.bitcoinj.core.ECKey;
 import org.bitcoinj.core.Utils;
 import static org.bitcoinj.core.Utils.HEX;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 
 public class AlertManager {
     private static final Logger log = LoggerFactory.getLogger(AlertManager.class);
 
     private final P2PService p2PService;
     private final KeyRing keyRing;
     private final User user;
     private final ObjectProperty<Alert> alertMessageProperty = new SimpleObjectProperty<>();
     private final boolean useDevPrivilegeKeys;
 
     private ECKey alertSigningKey;
 
 
     ///////////////////////////////////////////////////////////////////////////////////////////
     // Constructor, Initialization
     ///////////////////////////////////////////////////////////////////////////////////////////
 
     @Inject
     public AlertManager(P2PService p2PService,
                         KeyRing keyRing,
                         User user,
                         @Named(Config.IGNORE_DEV_MSG) boolean ignoreDevMsg,
                         @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
         this.p2PService = p2PService;
         this.keyRing = keyRing;
         this.user = user;
         this.useDevPrivilegeKeys = useDevPrivilegeKeys;
 
         if (!ignoreDevMsg) {
             p2PService.addHashSetChangedListener(new HashMapChangedListener() {
                 @Override
                 public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                     protectedStorageEntries.forEach(protectedStorageEntry -> {
                         final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
                         if (protectedStoragePayload instanceof Alert) {
                             Alert alert = (Alert) protectedStoragePayload;
                             if (verifySignature(alert))
                                 alertMessageProperty.set(alert);
                         }
                     });
                 }
 
                 @Override
                 public void onRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                     protectedStorageEntries.forEach(protectedStorageEntry -> {
                         final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
                         if (protectedStoragePayload instanceof Alert) {
                             if (verifySignature((Alert) protectedStoragePayload))
                                 alertMessageProperty.set(null);
                         }
                     });
                 }
             });
         }
     }
 
     protected List<String> getPubKeyList() {
         if (useDevPrivilegeKeys) return List.of(DevEnv.DEV_PRIVILEGE_PUB_KEY);
         switch (Config.baseCurrencyNetwork()) {
         case XMR_LOCAL:
             return List.of();
         case XMR_STAGENET:
             return List.of();
         case XMR_MAINNET:
             return List.of(
                 "038154884362237c5efceed1b559c340add1c8b527bbd832d2f07f317108e7fc84",
                 "023114de0484a55861c53edaca6667ea9f489954bfeaa6c5feb78ac9fdc028ca86",
                 "03eb5a83bc55a1e699ae61000bb4db38f69bea13cb82c63b95b93a8c6756faf182");
         default:
             throw new RuntimeException("Unhandled base currency network: " + Config.baseCurrencyNetwork());
         }
     }
 
 
     ///////////////////////////////////////////////////////////////////////////////////////////
     // API
     ///////////////////////////////////////////////////////////////////////////////////////////
 
     public ReadOnlyObjectProperty<Alert> alertMessageProperty() {
         return alertMessageProperty;
     }
 
     public boolean addAlertMessageIfKeyIsValid(Alert alert, String privKeyString) {
         // if there is a previous message we remove that first
         if (user.getDevelopersAlert() != null)
             removeAlertMessageIfKeyIsValid(privKeyString);
 
         boolean isKeyValid = isKeyValid(privKeyString);
         if (isKeyValid) {
             signAndAddSignatureToAlertMessage(alert);
             user.setDevelopersAlert(alert);
             boolean result = p2PService.addProtectedStorageEntry(alert);
             if (result) {
                 log.trace("Add alertMessage to network was successful. AlertMessage={}", alert);
             }
 
         }
         return isKeyValid;
     }
 
     public boolean removeAlertMessageIfKeyIsValid(String privKeyString) {
         Alert alert = user.getDevelopersAlert();
         if (isKeyValid(privKeyString) && alert != null) {
             if (p2PService.removeData(alert))
                 log.trace("Remove alertMessage from network was successful. AlertMessage={}", alert);
 
             user.setDevelopersAlert(null);
             return true;
         } else {
             return false;
         }
     }
 
     private boolean isKeyValid(String privKeyString) {
         try {
             alertSigningKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyString)));
             return getPubKeyList().contains(Utils.HEX.encode(alertSigningKey.getPubKey()));
         } catch (Throwable t) {
             return false;
         }
     }
 
     private void signAndAddSignatureToAlertMessage(Alert alert) {
         String alertMessageAsHex = Utils.HEX.encode(alert.getMessage().getBytes(Charsets.UTF_8));
         String signatureAsBase64 = alertSigningKey.signMessage(alertMessageAsHex);
         alert.setSigAndPubKey(signatureAsBase64, keyRing.getSignatureKeyPair().getPublic());
     }
 
     private boolean verifySignature(Alert alert) {
         String alertMessageAsHex = Utils.HEX.encode(alert.getMessage().getBytes(Charsets.UTF_8));
         for (String pubKeyAsHex : getPubKeyList()) {
             try {
                 ECKey.fromPublicOnly(HEX.decode(pubKeyAsHex)).verifyMessage(alertMessageAsHex, alert.getSignatureAsBase64());
                 return true;
             } catch (SignatureException e) {
                 // ignore
             }
         }
         log.warn("verifySignature failed");
         return false;
     }
 }
 