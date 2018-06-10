/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ihc.ws.projectfile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.openhab.binding.ihc.ws.datatypes.WSProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 *
 * @author Pauli Anttila - Initial contribution
 */
public class ProjectFileUtils {
    private final static Logger LOGGER = LoggerFactory.getLogger(ProjectFileUtils.class);

    public static Document readProjectFileFromFile(String path) {
        File fXmlFile = new File(path);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);
            return doc;
        } catch (IOException | ParserConfigurationException | SAXException e) {
            LOGGER.debug("Error occured when read project file from file '{}', reason {}", path, e.getMessage());
        }
        return null;
    }

    public static void saveProjectFile(String path, byte[] data) {
        try {
            FileUtils.writeByteArrayToFile(new File(path), data);
        } catch (IOException e) {
            LOGGER.warn("Error occured when trying to write data to file '{}', reason {}", path, e.getMessage());
        }
    }

    public static Document converteBytesToDocument(byte[] data) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(data));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            LOGGER.warn("Error occured when trying to convert data to XML, reason {}", e.getMessage());
        }
        return null;
    }

    public static boolean projectEqualsToControllerProject(Document projectfile, WSProjectInfo projectInfo) {
        if (projectInfo != null) {
            try {
                NodeList nodes = projectfile.getElementsByTagName("modified");
                if (nodes.getLength() == 1) {
                    Element node = (Element) nodes.item(0);
                    int year = Integer.parseInt(node.getAttribute("year"));
                    int month = Integer.parseInt(node.getAttribute("month"));
                    int day = Integer.parseInt(node.getAttribute("day"));
                    int hour = Integer.parseInt(node.getAttribute("hour"));
                    int minute = Integer.parseInt(node.getAttribute("minute"));

                    LOGGER.debug("Project file from file, date: {}.{}.{} {}:{}", year, month, day, hour, minute);
                    LOGGER.debug("Project file in controller, date: {}.{}.{} {}:{}",
                            projectInfo.getLastmodified().getYear(),
                            projectInfo.getLastmodified().getMonthWithJanuaryAsOne(),
                            projectInfo.getLastmodified().getDay(), projectInfo.getLastmodified().getHours(),
                            projectInfo.getLastmodified().getMinutes());

                    if (projectInfo.getLastmodified().getYear() == year
                            && projectInfo.getLastmodified().getMonthWithJanuaryAsOne() == month
                            && projectInfo.getLastmodified().getDay() == day
                            && projectInfo.getLastmodified().getHours() == hour
                            && projectInfo.getLastmodified().getMinutes() == minute) {
                        return true;
                    } else {
                        return false;
                    }
                }
            } catch (Exception e) {
                // do nothing, but return false
            }
        }
        return false;
    }
}
