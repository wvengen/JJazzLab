package org.jjazz.songmemoviewer;

import java.awt.event.ActionEvent;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.openide.util.Exceptions;

/**
 *
 * <p>
 */
public class InsertLinkDialog extends javax.swing.JDialog
{

    private URL link = null;
    private static final Logger LOGGER = Logger.getLogger(InsertLinkDialog.class.getSimpleName());

    /**
     * Creates new form InsertLinkDialog
     */
    public InsertLinkDialog(java.awt.Frame parent, boolean modal)
    {
        super(parent, modal);
        initComponents();
    }

    /**
     * Overridden to add global key bindings
     *
     * @return
     */
    @Override
    protected JRootPane createRootPane()
    {
        JRootPane contentPane = new JRootPane();
        contentPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "actionOk");   //NOI18N
        contentPane.getActionMap().put("actionOk", new AbstractAction("OK")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                btn_okActionPerformed(null);
            }
        });

        contentPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "actionCancel");   //NOI18N
        contentPane.getActionMap().put("actionCancel", new AbstractAction("Cancel")
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                btn_cancelActionPerformed(null);
            }
        });

        return contentPane;
    }

    /**
     * Get the link to insert.
     *
     * @return Null if user cancelled the dialog.
     */
    public URL getLink()
    {
        return link;
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of
     * this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        btnGroup = new javax.swing.ButtonGroup();
        rbtn_file = new javax.swing.JRadioButton();
        rbtn_external = new javax.swing.JRadioButton();
        textfield_file = new javax.swing.JTextField();
        btn_browse = new javax.swing.JButton();
        textfield_url = new javax.swing.JTextField();
        jScrollPane2 = new javax.swing.JScrollPane();
        helpTextArea1 = new org.jjazz.ui.utilities.HelpTextArea();
        btn_cancel = new javax.swing.JButton();
        btn_ok = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(org.openide.util.NbBundle.getBundle(InsertLinkDialog.class).getString("InsertLinkDialog.title")); // NOI18N

        btnGroup.add(rbtn_file);
        rbtn_file.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(rbtn_file, org.openide.util.NbBundle.getBundle(InsertLinkDialog.class).getString("InsertLinkDialog.rbtn_file.text")); // NOI18N

        btnGroup.add(rbtn_external);
        org.openide.awt.Mnemonics.setLocalizedText(rbtn_external, org.openide.util.NbBundle.getBundle(InsertLinkDialog.class).getString("InsertLinkDialog.rbtn_external.text")); // NOI18N

        textfield_file.setText("jTextField1"); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(btn_browse, org.openide.util.NbBundle.getBundle(InsertLinkDialog.class).getString("InsertLinkDialog.btn_browse.text")); // NOI18N

        textfield_url.setText("http://www.website.com"); // NOI18N

        jScrollPane2.setBorder(null);

        helpTextArea1.setColumns(20);
        helpTextArea1.setRows(2);
        helpTextArea1.setText(org.openide.util.NbBundle.getBundle(InsertLinkDialog.class).getString("InsertLinkDialog.helpTextArea1.text")); // NOI18N
        jScrollPane2.setViewportView(helpTextArea1);

        org.openide.awt.Mnemonics.setLocalizedText(btn_cancel, org.openide.util.NbBundle.getMessage(InsertLinkDialog.class, "InsertLinkDialog.btn_cancel.text")); // NOI18N
        btn_cancel.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btn_cancelActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(btn_ok, org.openide.util.NbBundle.getBundle(InsertLinkDialog.class).getString("InsertLinkDialog.btn_ok.text")); // NOI18N
        btn_ok.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btn_okActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(textfield_url)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btn_ok, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_cancel))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 403, Short.MAX_VALUE)
                            .addComponent(textfield_file))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btn_browse))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(rbtn_external)
                            .addComponent(rbtn_file))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(19, 19, 19)
                .addComponent(rbtn_file)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(textfield_file, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btn_browse))
                .addGap(1, 1, 1)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(rbtn_external)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(textfield_url, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 29, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btn_cancel)
                    .addComponent(btn_ok))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btn_okActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btn_okActionPerformed
    {//GEN-HEADEREND:event_btn_okActionPerformed
        String txt = null;
        if (rbtn_file.isSelected() && !textfield_file.getText().isBlank())
        {
            txt = textfield_file.getText();
        } else
        {
            txt = textfield_url.getText();
        }
        try
        {
            link = new URL(txt);
        } catch (MalformedURLException ex)
        {
            Exceptions.printStackTrace(ex);
        }    
        LOGGER.severe("btn_okActionPerformed() TO BE COMPLETED!!!");
        setVisible(false);

    }//GEN-LAST:event_btn_okActionPerformed

    private void btn_cancelActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btn_cancelActionPerformed
    {//GEN-HEADEREND:event_btn_cancelActionPerformed
        link = null;
        setVisible(false);
    }//GEN-LAST:event_btn_cancelActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup btnGroup;
    private javax.swing.JButton btn_browse;
    private javax.swing.JButton btn_cancel;
    private javax.swing.JButton btn_ok;
    private org.jjazz.ui.utilities.HelpTextArea helpTextArea1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JRadioButton rbtn_external;
    private javax.swing.JRadioButton rbtn_file;
    private javax.swing.JTextField textfield_file;
    private javax.swing.JTextField textfield_url;
    // End of variables declaration//GEN-END:variables
}
