/*******************************************************************************
 * Copyright (c) 2011,2012 Torkild U. Resheim.
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Torkild U. Resheim - initial API and implementation
 *******************************************************************************/
package org.eclipse.mylyn.docs.epub.core;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl;
import org.eclipse.mylyn.docs.epub.core.ILogger.Severity;
import org.eclipse.mylyn.docs.epub.ocf.Container;
import org.eclipse.mylyn.docs.epub.ocf.OCFFactory;
import org.eclipse.mylyn.docs.epub.ocf.OCFPackage;
import org.eclipse.mylyn.docs.epub.ocf.RootFile;
import org.eclipse.mylyn.docs.epub.ocf.RootFiles;
import org.eclipse.mylyn.docs.epub.ocf.util.OCFResourceImpl;
import org.eclipse.mylyn.internal.docs.epub.core.EPUBFileUtil;

/**
 * Represents one EPUB file. Currently <b>only</b> version 2.0.1 of the EPUB specification is supported. One or more
 * publications can be added and will be a part of the distribution when packed. See the <a
 * href="http://idpf.org/epub/20/spec/OPS_2.0.1_draft.htm#Section1.2">OPS specification</a> for definitions of words and
 * terms.
 * <p>
 * The simplest usage of this API may look like the following:
 * </p>
 * 
 * <pre>
 * EPUB epub = new EPUB();
 * OPSPublication oebps = new OPS2Publication();
 * oebps.addItem(new File(&quot;chapter.xhtml&quot;));
 * epub.add(oebps);
 * epub.pack(new File(&quot;book.epub&quot;));
 * </pre>
 * <p>
 * This will create a new EPUB instance and an OPS (which is the typical content of an EPUB) with one chapter. The OPS
 * will have one chapter with contents from <b>chapter.xhtml</b> and the final result is an EPUB named <b>book.epub</b>.
 * </p>
 * 
 * @author Torkild U. Resheim
 * @see http://www.idpf.org/doc_library/epub/OPS_2.0.1_draft.htm
 * @see http://www.idpf.org/doc_library/epub/OPF_2.0.1_draft.htm
 */
public class EPUB {

	/** Version of the OCF specification used */
	private static final String OCF_VERSION = "2.0"; //$NON-NLS-1$

	/** OEBPS (OPS+OPF) MIME type */
	private static final String MIMETYPE_OEBPS = "application/oebps-package+xml"; //$NON-NLS-1$

	/** Suffix for OCF files */
	private static final String OCF_FILE_SUFFIX = "xml"; //$NON-NLS-1$

	/** The encoding to use for the OCF */
	private static final String OCF_FILE_ENCODING = "UTF-8"; //$NON-NLS-1$

	/** The container holding all the publications */
	private Container ocfContainer;

	private ILogger logger;

	/**
	 * Creates a new <b>empty</b> instance of an EPUB. Use {@link #add(OPSPublication)} and {@link #pack(File)} to add
	 * publications and ready the EPUB for distribution.
	 */
	public EPUB() {
		ocfContainer = OCFFactory.eINSTANCE.createContainer();
		RootFiles rootFiles = OCFFactory.eINSTANCE.createRootFiles();
		ocfContainer.setRootfiles(rootFiles);
		ocfContainer.setVersion(OCF_VERSION);
		registerOCFResourceFactory();
	}

	public EPUB(ILogger logger) {
		this();
		this.logger = logger;
	}

	/**
	 * Returns the container instance of the EPUB.
	 * 
	 * @return the container instance
	 */
	public Container getContainer() {
		return ocfContainer;
	}

	/**
	 * Adds a new OEBPS publication to the EPUB. Use {@link #add(File, String)} to add other types of content.
	 * 
	 * @param oebps
	 *            the publication to add.
	 */
	public void add(OPSPublication oebps) {
		RootFiles rootFiles = ocfContainer.getRootfiles();
		int count = rootFiles.getRootfiles().size();
		String rootFileName = count > 0 ? "OEBPS_" + count : "OEBPS"; //$NON-NLS-1$ //$NON-NLS-2$
		rootFileName += "/content.opf"; //$NON-NLS-1$
		RootFile rootFile = OCFFactory.eINSTANCE.createRootFile();
		rootFile.setFullPath(rootFileName);
		rootFile.setMediaType(MIMETYPE_OEBPS);
		rootFile.setPublication(oebps);
		rootFiles.getRootfiles().add(rootFile);
		log(MessageFormat.format("Adding root file \"{0}\" of type \"{1}\" to EPUB", rootFile.getFullPath(),
				rootFile.getMediaType()), Severity.VERBOSE);
	}

	/**
	 * Adds a new publication (or root file) to the EPUB. Use {@link #add(OPSPublication)} when adding an OEBPS
	 * publication.
	 * 
	 * @param file
	 *            the publication to add
	 * @param type
	 *            the MIME type of the publication
	 * @see #add(OPSPublication)
	 */
	public void add(File file, String type) {
		String name = type.substring(type.lastIndexOf('/') + 1, type.length()).toUpperCase();
		RootFiles rootFiles = ocfContainer.getRootfiles();
		int count = rootFiles.getRootfiles().size();
		String rootFileName = count > 0 ? name + "_" + count : name; //$NON-NLS-1$
		rootFileName += File.separator + file.getName();
		RootFile rootFile = OCFFactory.eINSTANCE.createRootFile();
		rootFile.setFullPath(rootFileName);
		rootFile.setMediaType(type);
		rootFile.setPublication(file);
		rootFiles.getRootfiles().add(rootFile);
		log(MessageFormat.format("Adding root file \"{0}\" of type \"{1}\" to EPUB", rootFile.getFullPath(),
				rootFile.getMediaType()), Severity.VERBOSE);
	}

	/**
	 * Returns a list of all <i>OPS publications</i> contained within the EPUB.
	 * 
	 * @return a list of all OPS publications
	 */
	public List<OPSPublication> getOPSPublications() {
		ArrayList<OPSPublication> publications = new ArrayList<OPSPublication>();
		EList<RootFile> rootFiles = ocfContainer.getRootfiles().getRootfiles();
		for (RootFile rootFile : rootFiles) {
			if (rootFile.getMediaType().equals(MIMETYPE_OEBPS)) {
				publications.add((OPSPublication) rootFile.getPublication());
			}
		}
		return publications;
	}

	/**
	 * Assembles the EPUB file using a temporary working folder. The folder will be deleted as soon as the assembly has
	 * completed.
	 * 
	 * @param epubFile
	 *            the target EPUB file
	 * @throws Exception
	 */
	public File pack(File epubFile) throws Exception {
		File workingFolder = File.createTempFile("epub_", null); //$NON-NLS-1$
		if (workingFolder.delete() && workingFolder.mkdirs()) {
			pack(epubFile, workingFolder);
		}
		deleteFolder(workingFolder);
		return workingFolder;
	}

	private void log(String message, Severity severity) {
		if (logger != null) {
			logger.log(message, severity);
		}
	}

	/**
	 * Assembles the EPUB file using the specified working folder. The contents of the working folder will <b>not</b> be
	 * removed when the operation has completed. If the temporary data is not interesting, use {@link #pack(File)}
	 * instead.
	 * 
	 * @param epubFile
	 *            the target EPUB file
	 * @param rootFolder
	 *            the root folder holding all the EPUB contents
	 * @throws Exception
	 * @see {@link #pack(File)}
	 */
	public void pack(File epubFile, File rootFolder) throws Exception {
		if (ocfContainer.getRootfiles().getRootfiles().isEmpty()) {
			throw new IllegalArgumentException("EPUB does not contain any publications"); //$NON-NLS-1$
		}
		rootFolder.mkdirs();
		if (rootFolder.isDirectory() || rootFolder.mkdirs()) {
			writeOCF(rootFolder);
			EList<RootFile> publications = ocfContainer.getRootfiles().getRootfiles();
			log(MessageFormat.format("Assembling EPUB file to \"{0}\"", epubFile.getAbsolutePath()), Severity.INFO);
			for (RootFile rootFile : publications) {
				Object publication = rootFile.getPublication();
				File root = new File(rootFolder.getAbsolutePath() + File.separator + rootFile.getFullPath());
				if (publication instanceof OPSPublication) {
					((OPSPublication) publication).addCompulsoryData();
					((OPSPublication) publication).pack(root);
				} else {
					if (rootFile.getPublication() instanceof File) {
						EPUBFileUtil.copy((File) rootFile.getPublication(), root);
					} else {
						throw new IllegalArgumentException("Unknown publication type"); //$NON-NLS-1$
					}
				}
			}
			EPUBFileUtil.zip(epubFile, rootFolder);
			log(MessageFormat.format(
					"Successfully created EPUB containing {0,choice,0#no publications|1#one publication|1<{0,number,integer} publications}",
					publications.size()), Severity.INFO);
		} else {
			throw new IOException("Could not create working folder in " + rootFolder.getAbsolutePath()); //$NON-NLS-1$
		}
	}

	/**
	 * Reads the <i>Open Container Format (OCF)</i> formatted list of contents of this EPUB. The result of this
	 * operation is placed in the {@link #ocfContainer} instance.
	 * 
	 * @param rootFolder
	 *            the folder where the EPUB was unpacked
	 * @throws IOException
	 * @see {@link #unpack(File)}
	 * @see {@link #unpack(File, File)}
	 */
	protected void readOCF(File rootFolder) throws IOException {
		// These file names are listed in the OCF specification and must not be
		// changed.
		File metaFolder = new File(rootFolder.getAbsolutePath() + File.separator + "META-INF"); //$NON-NLS-1$
		File containerFile = new File(metaFolder.getAbsolutePath() + File.separator + "container.xml"); //$NON-NLS-1$
		ResourceSet resourceSet = new ResourceSetImpl();
		URI fileURI = URI.createFileURI(containerFile.getAbsolutePath());
		Resource resource = resourceSet.createResource(fileURI);
		resource.load(null);
		ocfContainer = (Container) resource.getContents().get(0);
	}

	/**
	 * Registers a new resource factory for OCF data structures. This is normally done through Eclipse extension points
	 * but we also need to be able to create this factory without the Eclipse runtime.
	 */
	private void registerOCFResourceFactory() {
		// Register package so that it is available even without the Eclipse
		// runtime
		@SuppressWarnings("unused")
		OCFPackage packageInstance = OCFPackage.eINSTANCE;

		// Register the file suffix
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put(OCF_FILE_SUFFIX,
				new XMLResourceFactoryImpl() {

					@Override
					public Resource createResource(URI uri) {
						OCFResourceImpl xmiResource = new OCFResourceImpl(uri);
						Map<Object, Object> loadOptions = xmiResource.getDefaultLoadOptions();
						Map<Object, Object> saveOptions = xmiResource.getDefaultSaveOptions();
						// We use extended metadata
						saveOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
						loadOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
						// Required in order to correctly read in attributes
						loadOptions.put(XMLResource.OPTION_LAX_FEATURE_PROCESSING, Boolean.TRUE);
						// Treat "href" attributes as features
						loadOptions.put(XMLResource.OPTION_USE_ENCODED_ATTRIBUTE_STYLE, Boolean.TRUE);
						// UTF-8 encoding is required per specification
						saveOptions.put(XMLResource.OPTION_ENCODING, OCF_FILE_ENCODING);
						// Do not download any external DTDs.
						Map<String, Object> parserFeatures = new HashMap<String, Object>();
						parserFeatures.put("http://xml.org/sax/features/validation", Boolean.FALSE); //$NON-NLS-1$
						parserFeatures.put("http://apache.org/xml/features/nonvalidating/load-external-dtd", //$NON-NLS-1$
								Boolean.FALSE);
						loadOptions.put(XMLResource.OPTION_PARSER_FEATURES, parserFeatures);
						return xmiResource;
					}

				});
	}

	/**
	 * Unpacks the EPUB file to a temporary location and populates the data model with the content.
	 * 
	 * @param epubFile
	 *            the EPUB file to unpack
	 * @return the location when the EPUB is unpacked
	 * @throws Exception
	 * @see {@link #unpack(File, File)}
	 */
	public File unpack(File epubFile) throws Exception {
		File workingFolder = File.createTempFile("epub_", null); //$NON-NLS-1$
		workingFolder.deleteOnExit();
		if (workingFolder.delete() && workingFolder.mkdirs()) {
			unpack(epubFile, workingFolder);
		}
		return workingFolder;
	}

	/**
	 * Unpacks the given EPUB file into the specified destination and populates the data model with the content. Note
	 * that when the destination folder already exists or is empty the file EPUB will not be unpacked or verified, but
	 * the contents of the destination will be treated as an already unpacked EPUB. If this behaviour is not desired one
	 * should take steps to delete the folder prior to unpacking.
	 * <p>
	 * When performing the unpacking, the modification date of the destination folder will be set to the modification
	 * date of the source EPUB. Additionally the contents of the EPUB will retain the original modification date if set.
	 * </p>
	 * <p>
	 * Multiple OPS root files in the publication will populate the OCF container instance with one
	 * {@link OPSPublication} for each as expected. The contents of the data model starting with the OCF container will
	 * be replaced.
	 * </p>
	 * 
	 * @param epubFile
	 *            the EPUB file to unpack
	 * @param rootFolder
	 *            the destination folder
	 * @throws Exception
	 * @see {@link #unpack(File)} when destination is not interesting
	 * @see {@link #getContainer()} to obtain the container instance
	 * @see {@link #getOPSPublications()} to get a list of all contained OPS publications
	 */
	public void unpack(File epubFile, File rootFolder) throws Exception {
		if (!rootFolder.exists() || rootFolder.list().length == 0) {
			EPUBFileUtil.unzip(epubFile, rootFolder);
		}
		readOCF(rootFolder);
		EList<RootFile> rootFiles = ocfContainer.getRootfiles().getRootfiles();
		for (RootFile rootFile : rootFiles) {
			if (rootFile.getMediaType().equals(MIMETYPE_OEBPS)) {
				// XXX: Handle this better when adding support for EPUB 3
				OPSPublication ops = OPSPublication.getVersion2Instance();
				File root = new File(rootFolder.getAbsolutePath() + File.separator + rootFile.getFullPath());
				ops.unpack(root);
				rootFile.setPublication(ops);
			}
		}
	}

	/**
	 * Creates a new folder named META-INF and writes the required (as per the OPS specification) <b>container.xml</b>
	 * in that folder. This is part of the packing procedure.
	 * 
	 * @param rootFolder
	 *            the root folder
	 */
	private void writeOCF(File rootFolder) throws IOException {
		File metaFolder = new File(rootFolder.getAbsolutePath() + File.separator + "META-INF"); //$NON-NLS-1$
		if (metaFolder.mkdir()) {
			File containerFile = new File(metaFolder.getAbsolutePath() + File.separator + "container.xml"); //$NON-NLS-1$
			ResourceSet resourceSet = new ResourceSetImpl();
			// Register the packages to make it available during loading.
			URI fileURI = URI.createFileURI(containerFile.getAbsolutePath());
			Resource resource = resourceSet.createResource(fileURI);
			resource.getContents().add(ocfContainer);
			resource.save(null);
		}
	}

	/**
	 * Utility method for deleting a folder recursively.
	 * 
	 * @param folder
	 *            the folder to delete
	 */
	private void deleteFolder(File folder) {
		if (folder.isDirectory()) {
			String[] children = folder.list();
			for (String element : children) {
				deleteFolder(new File(folder, element));
			}
		}
		folder.delete();
	}
}
