/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *
 * Contributors:
 *     Peter Smith
 *     Christian Thiemann
 *******************************************************************************/
package org.boris.winrun4j;

/**
 * CT: moved everything into FileAssociationsImpl so that the user
 * can choose whether to save to HKEY_CURRENT_USER or HKEY_LOCAL_MACHINE.
 * http://msdn.microsoft.com/en-us/library/cc144148(v=vs.85).aspx
 * http://msdn.microsoft.com/en-us/library/ms724475(v=vs.85).aspx
 */
public class FileAssociations
{

    /** Registers file associations for the currently logged in user only. */
    public static final FileAssociationsImpl CURRENT_USER = new FileAssociationsImpl(
      new RegistryKey(RegistryKey.HKEY_CURRENT_USER, new String[] { "Software", "Classes" }));

    /** Register file associations for all users on this computer. */
    public static final FileAssociationsImpl LOCAL_MACHINE = new FileAssociationsImpl(
      new RegistryKey(RegistryKey.HKEY_LOCAL_MACHINE, new String[] { "Software", "Classes" }));

    /** Provides a unified view of CURRENT_USER and LOCAL_MACHINE file associations.
     * Avoid writing to it directly. */
    public static final FileAssociationsImpl CLASSES_ROOT = new FileAssociationsImpl(
      RegistryKey.HKEY_CLASSES_ROOT);

    // functions providing backwards compatibility
    public static FileAssociation load(String extension) { return CLASSES_ROOT.load(extension); }
    public static void save(FileAssociation fa) { CLASSES_ROOT.save(fa); }
    public static void delete(FileAssociation fa) { CLASSES_ROOT.delete(fa); }

    // implementation class that can write to arbitrary registry keys
    public static class FileAssociationsImpl {
        public RegistryKey rootKey;

        public FileAssociationsImpl(RegistryKey rootKey) { this.rootKey = rootKey; }

        public FileAssociation load(String extension) {
            RegistryKey k = rootKey.getSubKey(extension);
            FileAssociation fa = new FileAssociation(extension);
            fa.setName(k.getString(null));
            fa.setContentType(k.getString("Content Type"));
            fa.setPerceivedType(k.getString("PerceivedType"));
            RegistryKey ok = k.getSubKey("OpenWithList");
            String[] owk = ok.getSubKeyNames();
            if (owk != null) {
                for (int i = 0; i < owk.length; i++) {
                    fa.addOpenWith(owk[i]);
                }
            }
            if (fa.getName() == null) {
                return fa;
            }

            // Load FileType section
            RegistryKey n = new RegistryKey(rootKey, fa.getName());
            fa.setDescription(n.getString(null));
            RegistryKey di = new RegistryKey(n, "DefaultIcon");
            fa.setIcon(di.getString(null));
            RegistryKey sk = new RegistryKey(n, "shell");
            String[] skn = sk.getSubKeyNames();
            for (int i = 0; i < skn.length; i++) {
                FileVerb fv = new FileVerb(skn[i]);
                RegistryKey fvk = sk.getSubKey(skn[i]);
                fv.setLabel(fvk.getString(null));
                RegistryKey ck = fvk.getSubKey("command");
                fv.setCommand(ck.getString(null));
                RegistryKey dk = fvk.getSubKey("ddeexec");
                fv.setDDECommand(dk.getString(null));
                RegistryKey adk = dk.getSubKey("Application");
                fv.setDDEApplication(adk.getString(null));
                RegistryKey tdk = dk.getSubKey("Topic");
                fv.setDDETopic(tdk.getString(null));
                fa.put(fv);
            }

            return fa;
        }

        public void save(FileAssociation fa) {
            if (fa == null || fa.getExtension() == null || fa.getName() == null) {
                return;
            }
            // Create extension key if it doesn't exist
            RegistryKey k = rootKey.createSubKey(fa.getExtension());
            k.setString(null, fa.getName());
            if (fa.getContentType() != null)
                k.setString("Content Type", fa.getContentType());
            if (fa.getPerceivedType() != null)
                k.setString("PerceivedType", fa.getPerceivedType());
            int owc = fa.getOpenWithCount();
            if (owc > 0) {
                RegistryKey ow = k.createSubKey("OpenWithList");
                for (int i = 0; i < owc; i++) {
                    ow.createSubKey(fa.getOpenWith(i));
                }
            }
            RegistryKey n = rootKey.createSubKey(fa.getName());
            n.setString(null, fa.getDescription());
            if (fa.getIcon() != null)
                n.createSubKey("DefaultIcon").setString(null, fa.getIcon());
            String[] verbs = fa.getVerbs();
            if (verbs != null && verbs.length > 0) {
                RegistryKey s = n.createSubKey("shell");
                s.setString(null, verbs[0]);  // CT: added default verb
                for (int i = 0; i < verbs.length; i++) {
                    RegistryKey v = s.createSubKey(verbs[i]);
                    FileVerb fv = fa.getVerb(verbs[i]);
                    v.createSubKey("command").setString(null, fv.getCommand());
                    if (fv.getDDECommand() != null) {
                        RegistryKey d = v.createSubKey("ddeexec");
                        d.setString(null, fv.getDDECommand());  // CT: added proper DDE command
                        d.createSubKey("Application").setString(null, fv.getDDEApplication());
                        d.createSubKey("Topic").setString(null, fv.getDDETopic());
                    }
                }
            }
        }

        public void delete(FileAssociation fa) {
            if (fa == null || fa.getExtension() == null || fa.getName() == null) {
                return;
            }
            rootKey.deleteSubKey(fa.getExtension());
            rootKey.deleteSubKey(fa.getName());
        }
    }
}
