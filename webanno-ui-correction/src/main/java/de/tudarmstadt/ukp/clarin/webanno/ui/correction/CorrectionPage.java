/*
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.ui.correction;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getAddr;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getFirstSentenceAddress;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getNextPageFirstSentenceAddress;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getNumberOfPages;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getSentenceAddress;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectByAddr;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectSentenceAt;
import static org.apache.uima.fit.util.JCasUtil.selectFollowing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.jcas.JCas;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.wicketstuff.annotation.mount.MountPath;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.api.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorStateImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.SecurityUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotator;
import de.tudarmstadt.ukp.clarin.webanno.brat.util.BratAnnotatorUtility;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.ScriptDirection;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentStateTransition;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.LambdaAjaxLink;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.PreferencesUtil;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.AnnotationPreferencesModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.DocumentNamePanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.ExportModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.FinishImage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.FinishLink;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.GuidelineModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.detail.AnnotationDetailEditorPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.dialog.OpenModalWindowPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.SuggestionViewPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.CurationContainer;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.CurationUserSegmentForAnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.SourceListView;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.SuggestionBuilder;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.service.AnnotationSelection;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.service.CuratorUtil;
import de.tudarmstadt.ukp.clarin.webanno.webapp.core.app.ApplicationPageBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import wicket.contrib.input.events.EventType;
import wicket.contrib.input.events.InputBehavior;
import wicket.contrib.input.events.key.KeyType;

/**
 * This is the main class for the correction page. Displays in the lower panel the Automatically
 * annotated document and in the upper panel the corrected annotation
 *
 */
@MountPath("/correction.html")
public class CorrectionPage
    extends ApplicationPageBase
{
    private static final Log LOG = LogFactory.getLog(CorrectionPage.class);

    private static final long serialVersionUID = 1378872465851908515L;

    @SpringBean(name = "documentRepository")
    private RepositoryService repository;

    @SpringBean(name = "annotationService")
    private AnnotationService annotationService;

    @SpringBean(name = "userRepository")
    private UserDao userRepository;

    private CurationContainer curationContainer;
    private AnnotatorState bModel;

    private Label numberOfPages;
    private DocumentNamePanel documentNamePanel;
    private ModalWindow openDocumentsModal;
    
    private long currentprojectId;

    // Open the dialog window on first load
    private boolean firstLoad = true;

    private NumberTextField<Integer> gotoPageTextField;
    private int gotoPageAddress;
    private AnnotationDetailEditorPanel editor;

    private FinishImage finish;

    private SuggestionViewPanel correctionView;
    private BratAnnotator annotator;

    private Map<String, Map<Integer, AnnotationSelection>> annotationSelectionByUsernameAndAddress = new HashMap<String, Map<Integer, AnnotationSelection>>();

    private SourceListView curationSegment = new SourceListView();

    public CorrectionPage()
    {
        bModel = new AnnotatorStateImpl();
        bModel.setMode(Mode.CORRECTION);

        WebMarkupContainer sidebarCell = new WebMarkupContainer("sidebarCell") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onComponentTag(ComponentTag aTag)
            {
                super.onComponentTag(aTag);
                aTag.put("width", bModel.getPreferences().getSidebarSize()+"%");
            }
        };
        add(sidebarCell);

        WebMarkupContainer annotationViewCell = new WebMarkupContainer("annotationViewCell") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onComponentTag(ComponentTag aTag)
            {
                super.onComponentTag(aTag);
                aTag.put("width", (100-bModel.getPreferences().getSidebarSize())+"%");
            }
        };
        add(annotationViewCell);
        
        LinkedList<CurationUserSegmentForAnnotationDocument> sentences = new LinkedList<CurationUserSegmentForAnnotationDocument>();
        CurationUserSegmentForAnnotationDocument curationUserSegmentForAnnotationDocument = new CurationUserSegmentForAnnotationDocument();
        if (bModel.getDocument() != null) {
            curationUserSegmentForAnnotationDocument
                    .setAnnotationSelectionByUsernameAndAddress(annotationSelectionByUsernameAndAddress);
            curationUserSegmentForAnnotationDocument.setBratAnnotatorModel(bModel);
            sentences.add(curationUserSegmentForAnnotationDocument);
        }
        correctionView = new SuggestionViewPanel("correctionView",
                new Model<LinkedList<CurationUserSegmentForAnnotationDocument>>(sentences))
        {
            private static final long serialVersionUID = 2583509126979792202L;

            @Override
            public void onChange(AjaxRequestTarget aTarget)
            {
                aTarget.addChildren(getPage(), FeedbackPanel.class);
                try {
                    // update begin/end of the curationsegment based on bratAnnotatorModel changes
                    // (like sentence change in auto-scroll mode,....
                    curationContainer.setBratAnnotatorModel(bModel);
                    setCurationSegmentBeginEnd();

                    CuratorUtil.updatePanel(aTarget, this, curationContainer, annotator,
                            repository, annotationSelectionByUsernameAndAddress, curationSegment,
                            annotationService, userRepository);
                    
                    annotator.bratRenderLater(aTarget);
                    aTarget.add(numberOfPages);
                    update(repository.readCorrectionCas(bModel.getDocument()), aTarget);
                }
                catch (UIMAException e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (Exception e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(e.getMessage());
                }
            }
        };

        correctionView.setOutputMarkupId(true);
        annotationViewCell.add(correctionView);

        editor = new AnnotationDetailEditorPanel(
                "annotationDetailEditorPanel", new Model<AnnotatorState>(bModel))
        {
            private static final long serialVersionUID = 2857345299480098279L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                aTarget.addChildren(getPage(), FeedbackPanel.class);
                aTarget.add(correctionView);
                aTarget.add(numberOfPages);

                try {
                    AnnotatorState state = getModelObject();
                    JCas annotationCas = getCas();
                    JCas correctionCas = repository.readCorrectionCas(state.getDocument());
                    annotator.bratRender(aTarget, annotationCas);
                    annotator.bratSetHighlight(aTarget, state.getSelection().getAnnotation());

                    // info(bratAnnotatorModel.getMessage());
                    SuggestionBuilder builder = new SuggestionBuilder(repository,
                            annotationService, userRepository);
                    curationContainer = builder.buildCurationContainer(bModel);
                    setCurationSegmentBeginEnd();
                    curationContainer.setBratAnnotatorModel(bModel);

                    CuratorUtil.updatePanel(aTarget, correctionView, curationContainer, annotator,
                            repository, annotationSelectionByUsernameAndAddress, curationSegment,
                            annotationService, userRepository);
                    
                    update(correctionCas, aTarget);
                }
                catch (UIMAException e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (Exception e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(e.getMessage());
                }
            }

            @Override
            protected void onAutoForward(AjaxRequestTarget aTarget)
            {
                try {
                    annotator.bratRender(aTarget, getCas());
                }
                catch (Exception e) {
                    LOG.error("Error reading CAS " + e.getMessage());
                    error("Error reading CAS " + e.getMessage());
                    return;
                }
            }
        };

        editor.setOutputMarkupId(true);
        sidebarCell.add(editor);

        annotator = new BratAnnotator("mergeView", new Model<AnnotatorState>(bModel), editor);
        annotator.setOutputMarkupId(true);
        annotationViewCell.add(annotator);

        curationContainer = new CurationContainer();
        curationContainer.setBratAnnotatorModel(bModel);

        add(documentNamePanel = new DocumentNamePanel("documentNamePanel",
                new Model<AnnotatorState>(bModel)));
        documentNamePanel.setOutputMarkupId(true);

        add(numberOfPages = (Label) new Label("numberOfPages",
                new LoadableDetachableModel<String>()
                {
                    private static final long serialVersionUID = 891566759811286173L;

                    @Override
                    protected String load()
                    {
                        if (bModel.getDocument() != null) {

                            JCas mergeJCas = null;
                            try {
                                mergeJCas = repository.readCorrectionCas(bModel
                                        .getDocument());

                                int totalNumberOfSentence = getNumberOfPages(mergeJCas);

                                List<SourceDocument> listofDoc = getListOfDocs();
                            	
                                int docIndex = listofDoc.indexOf(bModel.getDocument()) + 1;
                            	
                                return "showing " + bModel.getFirstVisibleSentenceNumber() + "-"
                                        + bModel.getLastVisibleSentenceNumber() + " of "
                                        + totalNumberOfSentence + " sentences [document " + docIndex
                                        + " of " + listofDoc.size() + "]";
                            }
                            catch (Exception e) {
                                return "";
                            }
                        }
                        else {
                            return "";// no document yet selected
                        }

                    }
                }).setOutputMarkupId(true));

        add(openDocumentsModal = new ModalWindow("openDocumentsModal"));
        openDocumentsModal.setOutputMarkupId(true);
        openDocumentsModal.setInitialWidth(620);
        openDocumentsModal.setInitialHeight(440);
        openDocumentsModal.setResizable(true);
        openDocumentsModal.setWidthUnit("px");
        openDocumentsModal.setHeightUnit("px");
        openDocumentsModal.setTitle("Open document");

        add(new AnnotationPreferencesModalPanel("annotationLayersModalPanel", Model.of(bModel), editor)
        {
            private static final long serialVersionUID = -4657965743173979437L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                // Re-render the whole page because the width of the sidebar may have changed
                aTarget.add(CorrectionPage.this);
                
                curationContainer.setBratAnnotatorModel(bModel);
                try {
                    setCurationSegmentBeginEnd();
                    update(repository.readCorrectionCas(bModel.getDocument()), aTarget);
                    // mergeVisualizer.reloadContent(aTarget);
                    aTarget.appendJavaScript(
                            "Wicket.Window.unloadConfirmation = false;window.location.reload()");
                }
                catch (UIMAException e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(ExceptionUtils.getRootCauseMessage(e));
                }
                catch (Exception e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(e.getMessage());
                }
            }
        });

        add(new ExportModalPanel("exportModalPanel", Model.of(bModel))
        {
            private static final long serialVersionUID = -468896211970839443L;

            {
                setOutputMarkupId(true);
                setOutputMarkupPlaceholderTag(true);
            }

            @Override
            protected void onConfigure()
            {
                super.onConfigure();
                setVisible(bModel.getProject() != null
                        && (SecurityUtil.isAdmin(bModel.getProject(), repository, bModel.getUser())
                                || !bModel.getProject().isDisableExport()));
            }
        });

        gotoPageTextField = (NumberTextField<Integer>) new NumberTextField<Integer>("gotoPageText",
                new Model<Integer>(0));
        Form<Void> gotoPageTextFieldForm = new Form<Void>("gotoPageTextFieldForm");
        gotoPageTextFieldForm.add(new AjaxFormSubmitBehavior(gotoPageTextFieldForm, "submit")
        {
            private static final long serialVersionUID = -4549805321484461545L;

            @Override
            protected void onSubmit(AjaxRequestTarget aTarget)
            {
                if (gotoPageAddress == 0) {
                    aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
                    return;
                }
                
                aTarget.addChildren(getPage(), FeedbackPanel.class);
                JCas correctionCas = null;
                try {
                    correctionCas = repository.readCorrectionCas(bModel.getDocument());
                    if (bModel.getFirstVisibleSentenceAddress() != gotoPageAddress) {
                        Sentence sentence = selectByAddr(correctionCas, Sentence.class, gotoPageAddress);
                        bModel.setFirstVisibleSentence(sentence);

                        SuggestionBuilder builder = new SuggestionBuilder(repository,
                                annotationService, userRepository);
                        curationContainer = builder.buildCurationContainer(bModel);
                        setCurationSegmentBeginEnd();
                        curationContainer.setBratAnnotatorModel(bModel);
                        update(correctionCas, aTarget);
                        annotator.bratRenderLater(aTarget);
                    }
                }
                catch (UIMAException e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (Exception e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(e.getMessage());
                }
            }
        });

        gotoPageTextField.setType(Integer.class);
        gotoPageTextField.setMinimum(1);
        gotoPageTextField.setDefaultModelObject(1);
        add(gotoPageTextFieldForm.add(gotoPageTextField));
        gotoPageTextField.add(new AjaxFormComponentUpdatingBehavior("change")
        {
            private static final long serialVersionUID = -3853194405966729661L;

            @Override
            protected void onUpdate(AjaxRequestTarget target)
            {
                JCas mergeJCas = null;
                try {
                    mergeJCas = repository.readCorrectionCas(bModel.getDocument());
                    gotoPageAddress = getSentenceAddress(mergeJCas,
                            gotoPageTextField.getModelObject());
                }
                catch (UIMAException e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(ExceptionUtils.getRootCause(e));
                }
                catch (Exception e) {
                    LOG.error("Error: " + e.getMessage(), e);
                    error(e.getMessage());
                }

            }
        });

        finish = new FinishImage("finishImage", new LoadableDetachableModel<AnnotatorState>()
        {
            private static final long serialVersionUID = -2737326878793568454L;

            @Override
            protected AnnotatorState load()
            {
                return bModel;
            }
        });
        finish.setOutputMarkupId(true);

        add(new FinishLink("showYesNoModalPanel",
                new Model<AnnotatorState>(bModel), finish)
        {
            private static final long serialVersionUID = -4657965743173979437L;
            
            @Override
            public void onClose(AjaxRequestTarget aTarget)
            {
                super.onClose(aTarget);
                aTarget.add(editor);
            }
        });

        add(new LambdaAjaxLink("showOpenDocumentModal", this::actionOpenDocument));

        add(new LambdaAjaxLink("showPreviousDocument", this::actionShowPreviousDocument)
                .add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_up },
                        EventType.click)));

        add(new LambdaAjaxLink("showNextDocument", this::actionShowNextDocument)
                .add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_down },
                        EventType.click)));

        add(new LambdaAjaxLink("showNext", this::actionShowNextPage)
                .add(new InputBehavior(new KeyType[] { KeyType.Page_down }, EventType.click)));

        add(new LambdaAjaxLink("showPrevious", this::actionShowPreviousPage)
                .add(new InputBehavior(new KeyType[] { KeyType.Page_up }, EventType.click)));

        add(new LambdaAjaxLink("showFirst", this::actionShowFirstPage)
                .add(new InputBehavior(new KeyType[] { KeyType.Home }, EventType.click)));

        add(new LambdaAjaxLink("showLast", this::actionShowLastPage)
                .add(new InputBehavior(new KeyType[] { KeyType.End }, EventType.click)));

        add(new LambdaAjaxLink("gotoPageLink", this::actionGotoPage));
        
        add(new LambdaAjaxLink("toggleScriptDirection", this::actionToggleScriptDirection));
        
        add(new GuidelineModalPanel("guidelineModalPanel", Model.of(bModel)));
    }

	private List<SourceDocument> getListOfDocs() {
		String username = SecurityContextHolder.getContext().getAuthentication().getName();
		User user = userRepository.get(username);
		// List of all Source Documents in the project
		List<SourceDocument> listOfSourceDocuements = repository.listSourceDocuments(bModel.getProject());
		List<SourceDocument> sourceDocumentsInIgnoreState = new ArrayList<SourceDocument>();
		for (SourceDocument sourceDocument : listOfSourceDocuements) {
			if (repository.existsAnnotationDocument(sourceDocument, user) && repository
					.getAnnotationDocument(sourceDocument, user).getState().equals(AnnotationDocumentState.IGNORE)) {
				sourceDocumentsInIgnoreState.add(sourceDocument);
			}
		}

		listOfSourceDocuements.removeAll(sourceDocumentsInIgnoreState);
		return listOfSourceDocuements;
	}
    /**
     * for the first time the page is accessed, open the <b>open document dialog</b>
     */
    @Override
    public void renderHead(IHeaderResponse response)
    {
        super.renderHead(response);

        if (firstLoad) {
            response.render(OnLoadHeaderItem
                    .forScript("jQuery('#showOpenDocumentModal').trigger('click');"));
            firstLoad = false;
        }
    }
    
    private void setCurationSegmentBeginEnd()
        throws UIMAException, ClassNotFoundException, IOException
    {
        JCas jCas = repository.readAnnotationCas(bModel.getDocument(),
                bModel.getUser());

        final int sentenceAddress = getAddr(selectSentenceAt(jCas,
                bModel.getFirstVisibleSentenceBegin(),
                bModel.getFirstVisibleSentenceEnd()));

        final Sentence sentence = selectByAddr(jCas, Sentence.class, sentenceAddress);
        List<Sentence> followingSentences = selectFollowing(jCas, Sentence.class, sentence,
                bModel.getPreferences().getWindowSize());
        // Check also, when getting the last sentence address in the display window, if this is the
        // last sentence or the ONLY sentence in the document
        Sentence lastSentenceAddressInDisplayWindow = followingSentences.size() == 0 ? sentence
                : followingSentences.get(followingSentences.size() - 1);
        curationSegment.setBegin(sentence.getBegin());
        curationSegment.setEnd(lastSentenceAddressInDisplayWindow.getEnd());

    }

    private void updateSentenceAddress(JCas aJCas, AjaxRequestTarget aTarget)
        throws UIMAException, IOException, ClassNotFoundException
    {
        gotoPageAddress = WebAnnoCasUtil.getSentenceAddress(aJCas,
                gotoPageTextField.getModelObject());

        String labelText = "";
        if (bModel.getDocument() != null) {
            
            List<SourceDocument> listofDoc = getListOfDocs();
            
            int docIndex = listofDoc.indexOf(bModel.getDocument())+1;
            
            int totalNumberOfSentence = WebAnnoCasUtil.getNumberOfPages(aJCas);

            // If only one page, start displaying from sentence 1
            if (totalNumberOfSentence == 1) {
                bModel.setFirstVisibleSentence(WebAnnoCasUtil.getFirstSentence(aJCas));
            }

            labelText = "showing " + bModel.getFirstVisibleSentenceNumber() + "-"
                    + bModel.getLastVisibleSentenceNumber() + " of " + totalNumberOfSentence
                    + " sentences [document " + docIndex + " of " + listofDoc.size() + "]";
        }
        else {
            labelText = "";// no document yet selected
        }

        numberOfPages.setDefaultModelObject(labelText);
        aTarget.add(numberOfPages);
        aTarget.add(gotoPageTextField);
    }
    
    private void update(JCas aJCas, AjaxRequestTarget target)
        throws UIMAException, ClassNotFoundException, IOException, AnnotationException
    {
        CuratorUtil.updatePanel(target, correctionView, curationContainer, annotator, repository,
                annotationSelectionByUsernameAndAddress, curationSegment, annotationService,
                userRepository);

        gotoPageTextField.setModelObject(bModel.getFirstVisibleSentenceNumber());
        gotoPageAddress = getSentenceAddress(aJCas, gotoPageTextField.getModelObject());

        target.add(gotoPageTextField);
        target.add(correctionView);
        target.add(numberOfPages);
    }
    
    private void actionOpenDocument(AjaxRequestTarget aTarget)
    {
        bModel.getSelection().clear();
        openDocumentsModal.setContent(new OpenModalWindowPanel(openDocumentsModal.getContentId(),
                bModel, openDocumentsModal, Mode.CORRECTION));
        openDocumentsModal.setWindowClosedCallback(new ModalWindow.WindowClosedCallback()
        {
            private static final long serialVersionUID = -1746088901018629567L;

            @Override
            public void onClose(AjaxRequestTarget target)
            {
                if (bModel.getDocument() == null) {
                    setResponsePage(getApplication().getHomePage());
                    return;
                }

                target.addChildren(getPage(), FeedbackPanel.class);
                try {
                    bModel.setDocument(bModel.getDocument());
                    bModel.setProject(bModel.getProject());

                    actionLoadDocument(target);
                    setCurationSegmentBeginEnd();
                    update(repository.readCorrectionCas(bModel.getDocument()), target);

                    String username = SecurityContextHolder.getContext().getAuthentication()
                            .getName();
                    User user = userRepository.get(username);
                    editor.setEnabled(!FinishImage.isFinished(Model.of(bModel), user, repository));
                    editor.loadFeatureEditorModels(target);
                }
                catch (Exception e) {
                    LOG.error("Unable to load data", e);
                    error("Unable to load data: " + ExceptionUtils.getRootCauseMessage(e));
                }
                finish.setModelObject(bModel);
                target.add(finish);
                target.appendJavaScript(
                        "Wicket.Window.unloadConfirmation=false;window.location.reload()");
                target.add(documentNamePanel);
                target.add(numberOfPages);
            }
        });
        openDocumentsModal.show(aTarget);
    }

    /**
     * Show the previous document, if exist
     */
    private void actionShowPreviousDocument(AjaxRequestTarget aTarget)
    {
        editor.reset(aTarget);
        aTarget.addChildren(getPage(), FeedbackPanel.class);
        // List of all Source Documents in the project
        List<SourceDocument> listOfSourceDocuements = getListOfDocs();

        // Index of the current source document in the list
        int currentDocumentIndex = listOfSourceDocuements.indexOf(bModel.getDocument());

        // If the first the document
        if (currentDocumentIndex == 0) {
            aTarget.appendJavaScript("alert('This is the first document!')");
        }
        else {
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            bModel.setDocument(listOfSourceDocuements.get(currentDocumentIndex - 1));

            try {
                actionLoadDocument(aTarget);
                setCurationSegmentBeginEnd();
                update(repository.readCorrectionCas(bModel.getDocument()), aTarget);
            }
            catch (UIMAException e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(ExceptionUtils.getRootCause(e));
            }
            catch (Exception e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(e.getMessage());
            }

            finish.setModelObject(bModel);
            aTarget.add(finish);
            aTarget.add(documentNamePanel);
            annotator.bratRenderLater(aTarget);
        }
    }

    /**
     * Show the next document if exist
     */
    private void actionShowNextDocument(AjaxRequestTarget aTarget)
    {
        editor.reset(aTarget);
        aTarget.addChildren(getPage(), FeedbackPanel.class);
        // List of all Source Documents in the project
        List<SourceDocument> listOfSourceDocuements = getListOfDocs();

        // Index of the current source document in the list
        int currentDocumentIndex = listOfSourceDocuements.indexOf(bModel.getDocument());

        // If the first document
        if (currentDocumentIndex == listOfSourceDocuements.size() - 1) {
            aTarget.appendJavaScript("alert('This is the last document!')");
            return;
        }
        bModel.setDocument(listOfSourceDocuements.get(currentDocumentIndex + 1));

        try {
            actionLoadDocument(aTarget);
            setCurationSegmentBeginEnd();
            update(repository.readCorrectionCas(bModel.getDocument()), aTarget);
        }
        catch (UIMAException e) {
            LOG.error("Error: " + e.getMessage(), e);
            error(ExceptionUtils.getRootCause(e));
        }
        catch (Exception e) {
            LOG.error("Error: " + e.getMessage(), e);
            error(e.getMessage());
        }

        finish.setModelObject(bModel);
        aTarget.add(finish);
        aTarget.add(documentNamePanel);
        annotator.bratRenderLater(aTarget);
    }

    private void actionGotoPage(AjaxRequestTarget aTarget)
    {
        if (gotoPageAddress == 0) {
            aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
            return;
        }
        if (bModel.getDocument() == null) {
            aTarget.appendJavaScript("alert('Please open a document first!')");
            return;
        }
        JCas correctionCas = null;
        try {
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            correctionCas = repository.readCorrectionCas(bModel.getDocument());
            if (bModel.getFirstVisibleSentenceAddress() != gotoPageAddress) {
                Sentence sentence = selectByAddr(correctionCas, Sentence.class, gotoPageAddress);
                bModel.setFirstVisibleSentence(sentence);

                SuggestionBuilder builder = new SuggestionBuilder(repository, annotationService,
                        userRepository);
                curationContainer = builder.buildCurationContainer(bModel);
                setCurationSegmentBeginEnd();
                curationContainer.setBratAnnotatorModel(bModel);
                update(correctionCas, aTarget);
                annotator.bratRenderLater(aTarget);
            }
        }
        catch (UIMAException e) {
            LOG.error("Error: " + e.getMessage(), e);
            error(ExceptionUtils.getRootCause(e));
        }
        catch (Exception e) {
            LOG.error("Error: " + e.getMessage(), e);
            error(e.getMessage());
        }
    }

    /**
     * SHow the previous page of this document
     */
    private void actionShowPreviousPage(AjaxRequestTarget aTarget)
    {
        if (bModel.getDocument() != null) {

            aTarget.addChildren(getPage(), FeedbackPanel.class);
            try {
                JCas correctionCas = repository.readCorrectionCas(bModel.getDocument());
                int previousSentenceAddress = WebAnnoCasUtil
                        .getPreviousDisplayWindowSentenceBeginAddress(correctionCas,
                                bModel.getFirstVisibleSentenceAddress(),
                                bModel.getPreferences().getWindowSize());
                if (bModel.getFirstVisibleSentenceAddress() != previousSentenceAddress) {
                    Sentence sentence = selectByAddr(correctionCas, Sentence.class,
                            previousSentenceAddress);
                    bModel.setFirstVisibleSentence(sentence);

                    SuggestionBuilder builder = new SuggestionBuilder(repository, annotationService,
                            userRepository);

                    curationContainer = builder.buildCurationContainer(bModel);
                    setCurationSegmentBeginEnd();
                    curationContainer.setBratAnnotatorModel(bModel);
                    update(correctionCas, aTarget);
                    annotator.bratRenderLater(aTarget);
                }
                else {
                    aTarget.appendJavaScript("alert('This is First Page!')");
                }
            }
            catch (UIMAException e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(ExceptionUtils.getRootCause(e));
            }
            catch (Exception e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(e.getMessage());
            }
        }
        else {
            aTarget.appendJavaScript("alert('Please open a document first!')");
        }
    }

    /**
     * Show the next page of this document
     */
    private void actionShowNextPage(AjaxRequestTarget aTarget)
    {
        if (bModel.getDocument() != null) {
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            try {
                JCas correctionCas = repository.readCorrectionCas(bModel.getDocument());
                int address = getAddr(
                        selectSentenceAt(correctionCas, bModel.getFirstVisibleSentenceBegin(),
                                bModel.getFirstVisibleSentenceEnd()));
                int nextSentenceAddress = getNextPageFirstSentenceAddress(correctionCas, address,
                        bModel.getPreferences().getWindowSize());
                if (address != nextSentenceAddress) {
                    Sentence sentence = selectByAddr(correctionCas, Sentence.class,
                            nextSentenceAddress);
                    bModel.setFirstVisibleSentence(sentence);

                    SuggestionBuilder builder = new SuggestionBuilder(repository, annotationService,
                            userRepository);
                    curationContainer = builder.buildCurationContainer(bModel);
                    setCurationSegmentBeginEnd();
                    curationContainer.setBratAnnotatorModel(bModel);
                    update(correctionCas, aTarget);
                    annotator.bratRenderLater(aTarget);
                }
                else {
                    aTarget.appendJavaScript("alert('This is last page!')");
                }
            }
            catch (UIMAException e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(ExceptionUtils.getRootCause(e));
            }
            catch (Exception e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(e.getMessage());
            }
        }
        else {
            aTarget.appendJavaScript("alert('Please open a document first!')");
        }
    }

    private void actionShowFirstPage(AjaxRequestTarget aTarget)
    {
        if (bModel.getDocument() != null) {
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            try {
                JCas correctionCas = repository.readCorrectionCas(bModel.getDocument());

                int address = getAddr(
                        selectSentenceAt(correctionCas, bModel.getFirstVisibleSentenceBegin(),
                                bModel.getFirstVisibleSentenceEnd()));
                int firstAddress = getFirstSentenceAddress(correctionCas);

                if (firstAddress != address) {
                    Sentence sentence = selectByAddr(correctionCas, Sentence.class, firstAddress);
                    bModel.setFirstVisibleSentence(sentence);

                    SuggestionBuilder builder = new SuggestionBuilder(repository, annotationService,
                            userRepository);
                    curationContainer = builder.buildCurationContainer(bModel);
                    setCurationSegmentBeginEnd();
                    curationContainer.setBratAnnotatorModel(bModel);
                    update(correctionCas, aTarget);
                    annotator.bratRenderLater(aTarget);
                }
                else {
                    aTarget.appendJavaScript("alert('This is first page!')");
                }
            }
            catch (UIMAException e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(ExceptionUtils.getRootCause(e));
            }
            catch (Exception e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(e.getMessage());
            }
        }
        else {
            aTarget.appendJavaScript("alert('Please open a document first!')");
        }
    }

    private void actionShowLastPage(AjaxRequestTarget aTarget)
    {
        if (bModel.getDocument() != null) {
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            try {
                JCas correctionCas = repository.readCorrectionCas(bModel.getDocument());
                int lastDisplayWindowBeginingSentenceAddress = WebAnnoCasUtil
                        .getLastDisplayWindowFirstSentenceAddress(correctionCas,
                                bModel.getPreferences().getWindowSize());
                if (lastDisplayWindowBeginingSentenceAddress != bModel
                        .getFirstVisibleSentenceAddress()) {
                    Sentence sentence = selectByAddr(correctionCas, Sentence.class,
                            lastDisplayWindowBeginingSentenceAddress);
                    bModel.setFirstVisibleSentence(sentence);

                    SuggestionBuilder builder = new SuggestionBuilder(repository, annotationService,
                            userRepository);
                    curationContainer = builder.buildCurationContainer(bModel);
                    setCurationSegmentBeginEnd();
                    curationContainer.setBratAnnotatorModel(bModel);
                    update(correctionCas, aTarget);
                    annotator.bratRenderLater(aTarget);
                }
                else {
                    aTarget.appendJavaScript("alert('This is last Page!')");
                }
            }
            catch (UIMAException e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(ExceptionUtils.getRootCause(e));
            }
            catch (Exception e) {
                LOG.error("Error: " + e.getMessage(), e);
                error(e.getMessage());
            }
        }
        else {
            aTarget.appendJavaScript("alert('Please open a document first!')");
        }
    }

    private void actionToggleScriptDirection(AjaxRequestTarget aTarget)
    {
        if (ScriptDirection.LTR.equals(bModel.getScriptDirection())) {
            bModel.setScriptDirection(ScriptDirection.RTL);
        }
        else {
            bModel.setScriptDirection(ScriptDirection.LTR);
        }

        try {
            curationContainer.setBratAnnotatorModel(bModel);
            CuratorUtil.updatePanel(aTarget, correctionView, curationContainer, annotator,
                    repository, annotationSelectionByUsernameAndAddress, curationSegment,
                    annotationService, userRepository);
        }
        catch (Exception e) {
            error(e);
            LOG.error(e);
        }

        annotator.bratRenderLater(aTarget);
    }

    private void actionLoadDocument(AjaxRequestTarget aTarget)
    {
        LOG.info("BEGIN LOAD_DOCUMENT_ACTION");

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.get(username);

        bModel.setUser(user);

        try {
            // Check if there is an annotation document entry in the database. If there is none,
            // create one.
            AnnotationDocument annotationDocument = repository
                    .createOrGetAnnotationDocument(bModel.getDocument(), user);

            // Read the correction CAS - if it does not exist yet, from the initial CAS
            JCas correctionCas;
            if (repository.existsCorrectionCas(bModel.getDocument())) {
                correctionCas = repository.readCorrectionCas(bModel.getDocument());
            }
            else {
                correctionCas = repository.createOrReadInitialCas(bModel.getDocument());
            }

            // Read the annotation CAS or create an annotation CAS from the initial CAS by stripping
            // annotations
            JCas annotationCas;
            if (repository.existsCas(bModel.getDocument(), user.getUsername())) {
                annotationCas = repository.readAnnotationCas(annotationDocument);
            }
            else {
                annotationCas = repository.createOrReadInitialCas(bModel.getDocument());
                annotationCas = BratAnnotatorUtility.clearJcasAnnotations(annotationCas,
                        bModel.getDocument(), user, repository);
            }

            // Update the CASes
            repository.upgradeCas(annotationCas.getCas(), annotationDocument);
            repository.upgradeCorrectionCas(correctionCas.getCas(), bModel.getDocument());

            // After creating an new CAS or upgrading the CAS, we need to save it
            repository.writeAnnotationCas(annotationCas.getCas().getJCas(),
                    annotationDocument.getDocument(), user);
            repository.writeCorrectionCas(correctionCas, bModel.getDocument(), user);

            // (Re)initialize brat model after potential creating / upgrading CAS
            bModel.initForDocument(correctionCas, repository);

            // Load constraints
            bModel.setConstraints(repository.loadConstraints(bModel.getProject()));

            // Load user preferences
            PreferencesUtil.setAnnotationPreference(username, repository, annotationService, bModel,
                    Mode.CORRECTION);

            // if project is changed, reset some project specific settings
            if (currentprojectId != bModel.getProject().getId()) {
                bModel.clearRememberedFeatures();
            }

            currentprojectId = bModel.getProject().getId();

            LOG.debug("Configured BratAnnotatorModel for user [" + bModel.getUser() + "] f:["
                    + bModel.getFirstVisibleSentenceNumber() + "] l:["
                    + bModel.getLastVisibleSentenceNumber() + "] s:["
                    + bModel.getFocusSentenceNumber() + "]");

            gotoPageTextField.setModelObject(1);

            setCurationSegmentBeginEnd();
            updateSentenceAddress(correctionCas, aTarget);
            update(correctionCas, aTarget);

            // Re-render the whole page because the font size
            aTarget.add(CorrectionPage.this);

            // Update document state
            if (bModel.getDocument().getState().equals(SourceDocumentState.NEW)) {
                bModel.getDocument().setState(SourceDocumentStateTransition
                        .transition(SourceDocumentStateTransition.NEW_TO_ANNOTATION_IN_PROGRESS));
                repository.createSourceDocument(bModel.getDocument());
            }
        }
        catch (UIMAException e) {
            LOG.error("Error", e);
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            error(ExceptionUtils.getRootCauseMessage(e));
        }
        catch (Exception e) {
            LOG.error("Error", e);
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            error("Error: " + e.getMessage());
        }

        LOG.debug("Configured BratAnnotatorModel for user [" + bModel.getUser() + "] f:["
                + bModel.getFirstVisibleSentenceNumber() + "] l:["
                + bModel.getLastVisibleSentenceNumber() + "] s:[" + bModel.getFocusSentenceNumber()
                + "]");

        LOG.info("END LOAD_DOCUMENT_ACTION");
    }
}