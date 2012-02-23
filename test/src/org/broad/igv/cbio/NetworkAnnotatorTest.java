/*
 * Copyright (c) 2007-2011 by The Broad Institute of MIT and Harvard.All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR
 * WARRANTIES OF ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING,
 * WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS, WHETHER
 * OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR RESPECTIVE
 * TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES
 * OF ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES,
 * ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER
 * THE BROAD OR MIT SHALL BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT
 * SHALL KNOW OF THE POSSIBILITY OF THE FOREGOING.
 */

package org.broad.igv.cbio;

import biz.source_code.base64Coder.Base64Coder;
import org.apache.commons.collections.Predicate;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.track.Track;
import org.broad.igv.track.TrackLoader;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.TestUtils;
import org.broad.igv.util.Utilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/**
 * User: jacob
 * Date: 2012/02/09
 */
public class NetworkAnnotatorTest {

    private String localpath = TestUtils.DATA_DIR + "/tp53network.xml";
    private NetworkAnnotator annotator;

//    //Copied on Feb 16, 2012, from "LoadFromServer" / Cancer Genome Atlas / GBM Subtypes
//    public static final String[] data_urls = new String[]{
//            "http://igv.broadinstitute.org/data/hg18/tcga/gbm/gbmsubtypes/sampleTable.txt.gz",
//    "http://igvdata.broadinstitute.org/data/hg18/tcga/gbm/gbmsubtypes/",
//    "http://igvdata.broadinstitute.org/data/hg18/tcga/gbm/gbmsubtypes/unifiedScaled.tab.gz",
//    "http://igvdata.broadinstitute.org/data/hg18/tcga/gbm/gbmsubtypes/TCGA_GBM_Level3_Somatic_Mutations_08.28.2008.maf.gz"
//    };

    @Before
    public void setUp() {
        annotator = new NetworkAnnotator();
    }

    @After
    public void tearDown() throws Exception {
        annotator = null;
        TestUtils.clearOutputDir();
    }

    @Test
    public void testLoadLocal() throws Exception {
        assertTrue("Failed to load network", annotator.loadNetwork(localpath));
    }

    @Test
    public void testFilter() throws Exception {
        Predicate tPred = new Predicate() {

            public boolean evaluate(Object object) {
                Node node = (Node) object;
                NamedNodeMap map = node.getAttributes();
                if (map == null) {
                    return false;
                }
                int id = Integer.parseInt(map.getNamedItem("id").getTextContent());
                return id % 2 == 0;
            }
        };

        annotator.loadNetwork(localpath);
        int removed = annotator.filterNodes(tPred);
        assertTrue(removed > 0);
    }

    /**
     * Load some data from cbio.
     * Checks that we are looking at the right urls
     *
     * @throws Exception
     */
    @Ignore
    @Test
    public void testDownloadCBIO() throws Exception {
        String[] gene_list = new String[]{"egfr", "brca1", "jun"};
        NetworkAnnotator anno = NetworkAnnotator.getFromCBIO(Arrays.asList(gene_list));
        assertNotNull(anno);
    }

    @Test
    public void testAnnotateAll() throws Exception {
        TestUtils.setUpHeadless();
        Genome genome = TestUtils.loadGenome();

        String networkPath = TestUtils.DATA_DIR + "/egfr_brca1.xml.gz";
        assertTrue(annotator.loadNetwork(networkPath));

        //Load some tracks
        String dataPath = TestUtils.DATA_DIR + "/seg/Broad.080528.subtypes.seg.gz";
        ResourceLocator locator = new ResourceLocator(dataPath);
        List<Track> tracks = new TrackLoader().load(locator, genome);
        annotator.annotateAll(tracks);

        //Check data
        NodeList nodes = annotator.getNodes();
        for (int nn = 0; nn < nodes.getLength(); nn++) {
            Node node = nodes.item(nn);
            for (String key : NetworkAnnotator.attribute_map.keySet()) {
                String data = NetworkAnnotator.getNodeKeyData(node, key);
                String name = NetworkAnnotator.getNodeKeyData(node, NetworkAnnotator.LABEL);
                if (!"CHMP3".equalsIgnoreCase(name)) {
                    assertNotNull(data);
                }
            }
        }

        //Check schema
        Document doc = annotator.getDocument();
        Node gml = doc.getFirstChild();
        for (String key : NetworkAnnotator.attribute_map.keySet()) {
            String data = NetworkAnnotator.getNodeAttrValue(gml, "id", key);
            assertNotNull(data);
        }

    }

    @Test
    public void testOutputNoGzip() throws Exception {
        String networkPath = TestUtils.DATA_DIR + "/egfr_brca1.xml.gz";
        assertTrue(annotator.loadNetwork(networkPath));
        String outPath = TestUtils.DATA_DIR + "/out/test.xml";
        tstOutputNetwork(annotator, outPath);
    }

    @Test
    public void testOutputGzip() throws Exception {
        String networkPath = TestUtils.DATA_DIR + "/egfr_brca1.xml.gz";
        assertTrue(annotator.loadNetwork(networkPath));
        String outPath = TestUtils.DATA_DIR + "/out/test.xml.gz";
        tstOutputNetwork(annotator, outPath);
    }


    public void tstOutputNetwork(NetworkAnnotator annotator, String outPath) throws Exception {
        NodeList nodes = annotator.getNodes();
        Set<String> nodeNames = new HashSet<String>();

        for (int nn = 0; nn < nodes.getLength(); nn++) {
            Node node = nodes.item(nn);
            nodeNames.add(NetworkAnnotator.getNodeKeyData(node, "label"));
        }

        assertTrue(annotator.writeDocument(outPath));


        NetworkAnnotator at = new NetworkAnnotator();
        assertTrue(at.loadNetwork(outPath));

        //Check that node set matches
        NodeList outNodes = at.getNodes();
        assertEquals("Output has a different number of nodes than input", nodes.getLength(), outNodes.getLength());
        for (int nn = 0; nn < outNodes.getLength(); nn++) {
            Node oNode = outNodes.item(nn);
            String nodeName = NetworkAnnotator.getNodeKeyData(oNode, "label");
            assertTrue(nodeNames.contains(nodeName));
        }

    }

    @Test
    public void testOutputForcBioView() throws Exception {
        assertTrue(annotator.loadNetwork(TestUtils.DATA_DIR + "/tp53network.xml"));
        String outPath = annotator.outputForcBioView();

        //Now attempt to read back in
        Document inDoc = Utilities.createDOMDocumentFromXmlStream(new FileInputStream(outPath));
        String b64data = inDoc.getElementsByTagName("textarea").item(0).getTextContent().trim();
        byte[] gzippedInput = Base64Coder.decode(b64data);
        InputStream plainData = new GZIPInputStream(new ByteArrayInputStream(gzippedInput));
        BufferedReader bufIn = new BufferedReader(new InputStreamReader(plainData));

        int count = 0;
        String[] outLines = NetworkAnnotator.getString(annotator.getDocument()).split("\n");
        String line;
        while ((line = bufIn.readLine()) != null) {
            assertEquals(outLines[count], line);
            count++;
        }


    }

}
