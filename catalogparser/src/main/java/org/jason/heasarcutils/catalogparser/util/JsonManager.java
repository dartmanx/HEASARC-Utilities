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

import java.io.*;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Class to handle the export to JSON
 * <p/>
 * Implemented (kind of) as a Builder pattern
 *
 * @author Jason Ferguson
 * @since 0.1
 */
@SuppressWarnings({"unused"})
public class JsonManager {

    private Catalog catalog;

    public JsonManager getJsonManager() {
        return this;
    }

    public JsonManager setCatalog(Catalog catalog) {
        this.catalog = catalog;
        return this;
    }

    /**
     * Method to determine what type of catalog is being imported and call the appropriate import method.
     */
    public void exportToJSON() {

        System.out.println("Exporting " + catalog.getName());
        if (catalog.getType().equals("tdat")) {
            // import tdat file
            String filename = catalog.getUrl();
            importTdatFile(filename);
        } else {
            // import dat file
            String filename = catalog.getUrl();
            importDatFile(filename);
        }

    }

    /**
     * Import a TDAT file
     *
     * @param fileURL String representing the URL of the file to import
     */
    private void importTdatFile(String fileURL) {

        String filename = getFilename(fileURL) + ".gz";

        try {
            URL url = new URL(fileURL);

            // set up input
            BufferedReader reader = getReader(fileURL);

            // set up output
            BufferedWriter writer = new BufferedWriter(new FileWriter(catalog.getName() + ".json"));

            // start processing
            while (reader.ready()) {
                String line = reader.readLine();
                if (!line.matches("^(.*?\\|)*$")) {
                    continue;
                }
                Map<String, String> result = new HashMap<String, String>();
                String[] fieldNames = catalog.getFieldData().keySet().toArray(new String[]{});
                String[] fieldValues = line.split("\\|");

                for (int i = 0; i < fieldValues.length; i++) {
                    FieldData fd = catalog.getFieldData().get(fieldNames[i]);
                    if (catalog.getFieldDataSet().contains(fd)) {
                        result.put(fieldNames[i], fieldValues[i]);
                    }
                }

                result = removeNulls(result);
                result = removeUnwantedFields(result, catalog);
                result = fixFieldPrefixes(result, catalog);
                result = fixFieldNames(result, catalog);

                writer.write(getJsonLine(result));

            }

            writer.close();
            reader.close();
            //gzis.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void importDatFile(String filename) {

        try {
            URL url = new URL(filename);
            // set up the data input
            BufferedReader reader = getReader(filename);

            // set up the data output
            BufferedWriter writer = new BufferedWriter(new FileWriter(catalog.getName() + ".json"));

            // create a template so I only have to create a map once
            Map<String, String> template = new LinkedHashMap<String, String>(catalog.getFieldData().size());
            for (String fieldName : catalog.getFieldData().keySet()) {
                template.put(fieldName, null);
            }

            while (reader.ready()) {
                String line = reader.readLine();
                Map<String, String> fieldMap = template;
                for (String key : catalog.getFieldData().keySet()) {
                    FieldData fd = catalog.getFieldData().get(key);
                    if (fd.getPrefix() != null) {
                        fieldMap.put(fd.getRenameTo(), line.substring(fd.getStart() - 1, fd.getEnd()).trim());
                    } else {
                        fieldMap.put(key, line.substring(fd.getStart() - 1, fd.getEnd()).trim());
                    }
                }

                fieldMap = removeNulls(fieldMap);
                fieldMap = fixFieldNames(fieldMap, catalog);
                fieldMap = fixFieldPrefixes(fieldMap, catalog);
                writer.write(getJsonLine(fieldMap));
                //line = reader.readLine();
            }

            writer.close();

            reader.close();
            //gzis.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private BufferedReader getReader(final String fileUrl) throws IOException {
        final String filename = getFilename(fileUrl) + ".gz";
        final URL url = new URL(fileUrl);
        final InputStream stream;
        if (new File(filename).isFile()) {
            stream = ClassLoader.getSystemResourceAsStream(fileUrl);
            System.out.println("Using tdat header from classes directory");
        } else {
            stream = url.openStream();
        }
        final GZIPInputStream gzipStream = new GZIPInputStream(stream);
        final InputStreamReader gzipStreamReader =
            new InputStreamReader(gzipStream, "UTF-8");
        final BufferedReader reader = new BufferedReader(gzipStreamReader);
        return reader;
    }

    private Map<String, String> removeNulls(Map<String, String> map) {

        Map<String, String> result = new LinkedHashMap<String, String>();

        for (String key : map.keySet()) {
            if (map.get(key) != null && map.get(key).length() > 0) {
                result.put(key, map.get(key));
            }
        }

        return result;
    }

    private Map<String, String> removeUnwantedFields(Map<String, String> data, Catalog catalog) {
        Map<String, String> result = new HashMap<String, String>();
        for (String key : catalog.getFieldData().keySet()) {
            FieldData fd = catalog.getFieldData().get(key);
            if (fd.isIncluded()) {
                if (data.get(key) != null) {
                    result.put(key, data.get(key));
                }
            }

        }
        return result;
    }

    /**
     * Determine if the field name needs to be prefixed and do so if necessary
     *
     * @param data
     * @param catalog
     * @return
     */
    private Map<String, String> fixFieldPrefixes(Map<String, String> data, Catalog catalog) {
        Map<String, String> result = new HashMap<String, String>();
        for (String key : catalog.getFieldData().keySet()) {
            FieldData fd = catalog.getFieldData().get(key);
            if (fd.isIncluded()) {
                if (data.get(key) != null) {
                    if (fd.getPrefix() != null && data.get(key).indexOf(fd.getPrefix()) == -1) {
                        result.put(key, fd.getPrefix() + data.get(key));
                    } else {
                        result.put(key, data.get(key));
                    }
                }
            }

        }

        return result;
    }

    /**
     * Determine if the field needs to be renamed and fix it if necessary
     *
     * @param data
     * @param catalog
     * @return
     */
    private Map<String, String> fixFieldNames(Map<String, String> data, Catalog catalog) {
        // Set result to be the input value, we'll remove values rather than add
        Map<String, String> result = data;
        // loop through the map of field data for the catalog configuration
        for (String key : catalog.getFieldData().keySet()) {
            // get the field data for the field identified by the key
            FieldData fd = catalog.getFieldData().get(key);

            // get the value to rename to
            String renameValue = fd.getRenameTo();

            // if there is a value to rename to, copy it to a new key representing the renamed value
            if (renameValue != null && renameValue.length() > 0) {
                // this is kind of a big-hammer approach, but I don't want to write null values to the
                // map containing the data, which may be returned from the following statement
                if (result.get(key) == null) {
                    continue;
                }

                result.put(renameValue, data.get(key));
                // if we don't keep it after the rename, drop the key
                if (!fd.isKeepAfterCopy()) {
                    result.remove(key);
                }
            }
        }

        return result;
    }

    private String getJsonLine(Map<String, String> data) {
        StringBuffer sb = new StringBuffer();
        sb.append("{");
        for (String key : data.keySet()) {
            sb.append(key);
            sb.append(":");
            if (isNumber(data.get(key))) {
                if (isInteger(data.get(key))) {
                    sb.append(new Integer(data.get(key).trim()));
                } else {
                    BigDecimal number = new BigDecimal(data.get(key).trim());
                    number = number.setScale(4, BigDecimal.ROUND_HALF_EVEN);
                    sb.append(number);
                }
            } else {
                sb.append("\"");
                sb.append(data.get(key));
                sb.append("\"");
            }
            sb.append(",");
        }
        sb = new StringBuffer(sb.substring(0, sb.length() - 1)); // stupid trailing comma
        sb.append("}\r\n");

        return sb.toString();
    }

    protected String getFilename(String url) {
        return url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.'));
    }

    private boolean isInteger(String value) {
        if (value == null) {
            return false;
        }
        String pattern = "^\\s*[\\+,-]?[0-9]+$";
        return value.matches(pattern);
    }

    private boolean isDouble(String value) {
        if (value == null) {
            return false;
        }
        String pattern = "^\\s*[\\+,-]?[0-9]*\\.[0-9]*$";
        return value.matches(pattern);
    }

    private boolean isNumber(String value) {
        return (isInteger(value) || isDouble(value));
    }
}
