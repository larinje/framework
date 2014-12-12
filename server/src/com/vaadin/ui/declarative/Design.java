/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.ui.declarative;

import java.beans.IntrospectionException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import com.vaadin.ui.Component;
import com.vaadin.ui.declarative.DesignContext.ComponentCreatedEvent;
import com.vaadin.ui.declarative.DesignContext.ComponentCreationListener;

/**
 * Design is used for parsing a component hierarchy from an html file and,
 * conversely, for generating an html tree representation corresponding to a
 * given component hierarchy. For both parsing and tree generation the component
 * hierarchy must contain a single root.
 * 
 * 
 * @since 7.4
 * @author Vaadin Ltd
 */
public class Design implements Serializable {
    /**
     * Parses the given input stream into a jsoup document
     *
     * @param html
     *            the stream containing the design
     * @return the parsed jsoup document
     * @throws IOException
     */
    private static Document parse(InputStream html) {
        try {
            Document doc = Jsoup.parse(html, "UTF-8", "", Parser.htmlParser());
            return doc;
        } catch (IOException e) {
            throw new DesignException("The html document cannot be parsed.");
        }

    }

    /**
     * Constructs a component hierarchy from the design specified as an html
     * document. The component hierarchy must contain exactly one top-level
     * Component. The component should be located under <body>, but also invalid
     * html containing the hierarchy without <html>, <head> and <body> tags is
     * accepted. You can optionally pass instance for the root component with
     * some uninitialized instance fields. The fields will be automatically
     * populated when parsing the design based on the component ids, local ids,
     * and captions of the components in the design.
     *
     * @param html
     *            the html document describing the component design
     * @param rootInstance
     *            the root instance with fields to be mapped to components in
     *            the design
     * @return the DesignContext created while traversing the tree. The
     *         top-level component of the created component hierarchy can be
     *         accessed using result.getRootComponent(), where result is the
     *         object returned by this method.
     * @throws IOException
     */
    private static DesignContext parse(InputStream html, Component rootInstance) {
        Document doc = parse(html);
        return designToComponentTree(doc, rootInstance);
    }

    /**
     * Constructs a component hierarchy from the design specified as an html
     * document given as a string. The component hierarchy must contain exactly
     * one top-level Component. The component should be located under <body>,
     * but also invalid html containing the hierarchy without <html>, <head> and
     * <body> tags is accepted. You can optionally pass instance for the root
     * component with some uninitialized instance fields. The fields will be
     * automatically populated when parsing the design based on the component
     * ids, local ids, and captions of the components in the design.
     *
     * @param html
     *            the html document describing the component design
     * @param rootInstance
     *            the root instance with fields to be mapped to components in
     *            the design
     * @return the DesignContext created while traversing the tree. The
     *         top-level component of the created component hierarchy can be
     *         accessed using result.getRootComponent(), where result is the
     *         object returned by this method.
     * @throws IOException
     */
    private static DesignContext parse(String html, Component rootInstance) {
        Document doc = Jsoup.parse(html);
        return designToComponentTree(doc, rootInstance);
    }

    /**
     * Constructs a component hierarchy from the design specified as an html
     * tree.
     * 
     * If a component root is given, the component instances created during
     * synchronizing the design are assigned to its member fields based on their
     * id, local id, and caption
     *
     * @param doc
     *            the html tree
     * @param componentRoot
     *            optional component root instance with some member fields. The
     *            type must match the type of the root element in the design.
     *            The member fields whose type is assignable from
     *            {@link Component} are set when parsing the component tree
     *
     */
    private static DesignContext designToComponentTree(Document doc,
            Component componentRoot) {
        DesignContext designContext = new DesignContext(doc);
        designContext.getPrefixes(doc);
        // No special handling for a document without a body element - should be
        // taken care of by jsoup.
        Element root = doc.body();
        Elements children = root.children();
        if (children.size() != 1) {
            throw new DesignException(
                    "The first level of a component hierarchy should contain exactly one root component, but found "
                            + children.size());
        }
        Element element = children.first();
        if (componentRoot != null) {
            // user has specified root instance that may have member fields that
            // should be bound
            FieldBinder binder = null;
            try {
                binder = new FieldBinder(componentRoot);
            } catch (IntrospectionException e) {
                throw new DesignException(
                        "Could not bind fields of the root component", e);
            }
            final FieldBinder fBinder = binder;
            // create listener for component creations that binds the created
            // components to the componentRoot instance fields
            ComponentCreationListener creationListener = new ComponentCreationListener() {
                @Override
                public void componentCreated(ComponentCreatedEvent event) {
                    fBinder.bindField(event.getComponent(), event.getLocalId());
                }
            };
            designContext.addComponentCreationListener(creationListener);
            // create subtree
            designContext.synchronizeAndRegister(componentRoot, element);
            // make sure that all the member fields are bound
            Collection<String> unboundFields = binder.getUnboundFields();
            if (!unboundFields.isEmpty()) {
                throw new DesignException(
                        "Found unbound fields from component root "
                                + unboundFields);
            }
            // no need to listen anymore
            designContext.removeComponentCreationListener(creationListener);
        } else {
            // createChild creates the entire component hierarchy
            componentRoot = designContext.createChild(element);
        }
        designContext.setRootComponent(componentRoot);
        return designContext;
    }

    /**
     * Generates an html tree representation representing the component
     * hierarchy having the given root. The hierarchy is stored under <body> in
     * the tree. The generated tree corresponds to a valid html document.
     *
     *
     * @param root
     *            the root of the component hierarchy
     * @return an html tree representation of the component hierarchy
     */
    private static Document createHtml(DesignContext designContext) {
        // Create the html tree skeleton.
        Document doc = new Document("");
        DocumentType docType = new DocumentType("html", "", "", "");
        doc.appendChild(docType);
        Element html = doc.createElement("html");
        doc.appendChild(html);
        html.appendChild(doc.createElement("head"));
        Element body = doc.createElement("body");
        html.appendChild(body);
        designContext.storePrefixes(doc);

        // Append the design under <body> in the html tree. createNode
        // creates the entire component hierarchy rooted at the
        // given root node.
        Component root = designContext.getRootComponent();
        Node rootNode = designContext.createNode(root);
        body.appendChild(rootNode);
        return doc;
    }

    /**
     * Generates an html file corresponding to the component hierarchy with the
     * given root.
     *
     * @param writer
     * @param root
     * @throws IOException
     */
    private static void createHtml(BufferedWriter writer, DesignContext ctx)
            throws IOException {
        String docAsString = createHtml(ctx).toString();
        writer.write(docAsString);
    }

    /**
     * Loads a design from the given file name using the given root component.
     * <p>
     * Any {@link Component} type fields in the root component which are not
     * assigned (i.e. are null) are mapped to corresponding components in the
     * design. Matching is done based on field name in the component class and
     * id/local id/caption in the design file.
     * <p>
     * The type of the root component must match the root element in the design
     * 
     * @param filename
     *            The file name to load. Loaded from the same package as the
     *            root component
     * @param rootComponent
     *            The root component of the layout.
     * @return The design context used in the load operation
     * @throws DesignException
     *             If the design could not be loaded
     */
    public static DesignContext read(String filename, Component rootComponent)
            throws DesignException {
        InputStream stream = rootComponent.getClass().getResourceAsStream(
                filename);
        if (stream == null) {
            throw new DesignException("File " + filename
                    + " was not found in the package "
                    + rootComponent.getClass().getPackage().getName());
        }
        return read(stream, rootComponent);
    }

    /**
     * Loads a design from the given stream using the given root component.
     * <p>
     * Any {@link Component} type fields in the root component which are not
     * assigned (i.e. are null) are mapped to corresponding components in the
     * design. Matching is done based on field name in the component class and
     * id/local id/caption in the design file.
     * <p>
     * The type of the root component must match the root element in the design
     * 
     * @param stream
     *            The stream to read the design from
     * @param rootComponent
     *            The root component of the layout.
     * @return The design context used in the load operation
     * @throws DesignException
     *             If the design could not be loaded
     */
    public static DesignContext read(InputStream design, Component rootComponent) {
        if (design == null) {
            throw new DesignException("Stream cannot be null");
        }
        Document doc = parse(design);
        DesignContext context = designToComponentTree(doc, rootComponent);

        return context;
    }

    /**
     * Loads a design from the given input stream
     * 
     * @param design
     *            The input stream which contains the design
     * @return The root component of the design
     */
    public static Component read(InputStream design) {
        DesignContext context = read(design, null);
        return context.getRootComponent();

    }

    /**
     * Writes the given component tree in design format to the given output
     * stream
     * 
     * @param component
     *            the root component of the component tree to write
     * @param outputStream
     *            the output stream to write the design to. The design is always
     *            written as UTF-8
     * @throws IOException
     */
    public static void write(Component component, OutputStream outputStream)
            throws IOException {
        DesignContext dc = new DesignContext();
        dc.setRootComponent(component);
        write(dc, outputStream);
    }

    /**
     * Writes the component, given in the design context, in design format to
     * the given output stream. The design context is used for writing local ids
     * and other information not available in the component tree.
     * 
     * @param component
     *            the root component of the component tree to write
     * @param outputStream
     *            the output stream to write the design to. The design is always
     *            written as UTF-8
     * @throws IOException
     *             if writing fails
     */
    public static void write(DesignContext designContext,
            OutputStream outputStream) throws IOException {
        Document doc = createHtml(designContext);
        write(doc, outputStream);
    }

    /**
     * Writes the given jsoup document to the output stream (in UTF-8)
     * 
     * @param doc
     *            the document to write
     * @param outputStream
     *            the stream to write to
     * @throws IOException
     *             if writing fails
     */
    private static void write(Document doc, OutputStream outputStream)
            throws IOException {
        doc.outputSettings().indentAmount(4);
        doc.outputSettings().syntax(Syntax.html);
        doc.outputSettings().prettyPrint(true);
        outputStream.write(doc.html().getBytes());
    }

}
