package org.graphast.importer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;

import org.graphast.model.Graph;
import org.graphast.model.Node;

public class POIImporter {

	public static void importPoIList(Graph graph, String path) throws NumberFormatException, IOException {

		InputStream is = new FileInputStream(path);
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);

		String row;
		String[] splittedRow;

		
		int[] poiCosts = new int[96];
		
		Random random = new Random();

		int minTime, maxTime;
		
		minTime = 1;
		maxTime = 300000;
		
		for(int i=0; i<96; i++) {
			poiCosts[i] = random.nextInt(maxTime-minTime)+minTime;
		}
		
		
		while((row = br.readLine()) != null ) {

			splittedRow = row.split(";");

			Node n = graph.getNearestNode(Double.parseDouble(splittedRow[2]), Double.parseDouble(splittedRow[3]));

			n.setCategory(Integer.parseInt(splittedRow[0]));
			n.setLabel(splittedRow[4]);

			n.setCosts(poiCosts);
			
			graph.updateNodeInfo(n);
//			System.out.println(graph.getNearestNode(Double.parseDouble(splittedRow[2]), Double.parseDouble(splittedRow[3])).getCategory());
		}

		br.close();

	}

}