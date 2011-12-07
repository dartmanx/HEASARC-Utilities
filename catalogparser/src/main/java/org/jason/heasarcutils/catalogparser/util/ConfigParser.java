/**
 * Copyright 2011 Jason Ferguson.
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
package org.jason.heasarcutils.catalogparser.util;

import org.jason.heasarcutils.catalogparser.misc.ConfigurationParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsers for configuration XML. Uses DOM for XML processing, not SAX
 *
 * @author Jason Ferguson
 * @since 0.1
 */
public class ConfigParser {

    public Map<String, Object> getConfig(String configFile) {
        Map<String, Object> resultMap = new HashMap<String, Object>();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(new File(configFile)); // TODO: This should probably use getResourceAsStream
            NodeList catalogNodes = document.getElementsByTagName("catalog");
            for (int i = 0; i < catalogNodes.getLength(); i++) {
                Catalog catalog = new Catalog();
                Element catalogNode = (Element) catalogNodes.item(i);

                String name = catalogNode.getAttribute("name");
                if (name.isEmpty()) {
                    throw new ConfigurationParseException("Attribute 'name' of tag 'catalog' cannot be empty");
                }
                String type = catalogNode.getAttribute("type");
                if (type == null || type.isEmpty()) {
                    throw new ConfigurationParseException("Attribute 'type' of tag 'catalog' cannot be null or empty");
                }
                if (!(type.equals("tdat") || type.equals("dat"))) {
                    throw new ConfigurationParseException("Attribute value for 'type' must be 'tdat' or 'dat");
                }
                catalog.setName(name);
                if (type.equals("tdat")) {
                    catalog = getTdatConfig(catalogNode);
                } else {
                    // field lengths are constant
                }
                resultMap.put(name, catalog);
            }
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        }

        return resultMap;
    }

    private Catalog getTdatConfig(Element catalogNode) {

        Catalog catalog = new Catalog();
        // set the initial values
        catalog.setName(catalogNode.getAttribute("name"));
        catalog.setUrl(getUrl(catalogNode));
        catalog.setHeaderUrl(getHeaderUrl(catalogNode));
        catalog.setEpoch(getEpoch(catalogNode));
        catalog.setFieldData(getFieldData(catalogNode));

        // update catalog with exceptions to basic data
        Set<String> includedFields= catalog.getFieldData().keySet();
        String[] fields = getFieldNamesFromTdatHeader(getTextValue(catalogNode, "name"));
        for (String field: fields) {
            if (!includedFields.contains(field)) {
                FieldData holder = new FieldData();
                holder.setExcluded(true);
                catalog.getFieldData().put(field, holder);
            }
        }

        return catalog;
    }

    private String[] getFieldNamesFromTdatHeader(String headerFile) {
        String[] fields = null;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(headerFile));
            String line = reader.readLine();
            while (line != null) {
                if (line.matches("line\\[1\\] = (.*)")) {
                    Pattern pattern = Pattern.compile("line\\[1\\] = (.*)");
                    Matcher matcher = pattern.matcher(line);
                    fields = matcher.group(1).split("\\s");
                    break;
                }

                line = reader.readLine();
            }
            reader.close();
        } catch (FileNotFoundException e) {

        } catch (IOException e) {

        }
        return fields;
    }
    private Catalog getDatConfig() {

        return null;
    }

    private static String getTextValue(Element ele, String tagName) {
        String textVal = null;
        NodeList nl = ele.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            Element el = (Element) nl.item(0);
            textVal = el.getFirstChild().getNodeValue();
        }

        return textVal;
    }


    private String getUrl(Element catalogNode) {
        String url = getTextValue(catalogNode, "url");
        if (url.isEmpty()) {
            throw new ConfigurationParseException("Attribute 'url' cannot be empty.");
        }
        return url;
    }

    private String getHeaderUrl(Element catalogNode) {
        String headerUrl = getTextValue(catalogNode, "headerUrl");
        if (headerUrl.isEmpty()) {
            throw new ConfigurationParseException("Attribute 'headerUrl' cannot be empty.");
        }

        return headerUrl;
    }

    private String getEpoch(Element catalogNode) {
        String epoch = getTextValue(catalogNode, "epoch");
        if (epoch.isEmpty()) {
            throw new ConfigurationParseException("Attribute 'epoch' cannot be empty");
        }
        return epoch;
    }

    private Map<String, FieldData> getFieldData(Element catalogNode) {
        Map<String, FieldData> resultMap = new HashMap<String, FieldData>();

        Element fields = (Element) catalogNode.getElementsByTagName("fields").item(0);
        NodeList fieldNodeList = fields.getElementsByTagName("field");
        for (int i = 0; i < fieldNodeList.getLength(); i++) {
            Element fieldNode = (Element) fieldNodeList.item(i);
            String name = fieldNode.getAttribute("name");
            if (name.isEmpty()) {
                throw new ConfigurationParseException("Attribute 'name' of tag 'field' cannot be empty.");
            }
            String rename = fieldNode.getAttribute("renameTo");
            String prefix = fieldNode.getAttribute("prefix");
            String keepAfterCopy = fieldNode.getAttribute("keepAfterCopy");
            String start = fieldNode.getAttribute("start");
            String end = fieldNode.getAttribute("end");

            FieldData fd = new FieldData();
            fd.setName(name);
            if (!rename.isEmpty()) {
                fd.setRenameTo(rename);
            }
            if (!prefix.isEmpty()) {
                fd.setPrefix(prefix);
            }
            if (!keepAfterCopy.isEmpty()) {
                boolean kac = Boolean.valueOf(keepAfterCopy);
                fd.setKeepAfterCopy(kac);
            }
            if (isInteger(start)) {
                fd.setStart(Integer.parseInt(start));
            }
            if (isInteger(end)) {
                fd.setEnd(Integer.parseInt(end));
            }
            resultMap.put(name, fd);
        }

        return resultMap;
    }

    private boolean isInteger(String value) {
        String pattern = "^[0-9]+$";
        return value.matches(pattern);
    }
}
