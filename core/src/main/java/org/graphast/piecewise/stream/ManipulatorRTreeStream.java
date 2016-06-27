package org.graphast.piecewise.stream;

import org.graphast.piecewise.Function;
import org.graphast.piecewise.IGeneratorFunction;
import org.graphast.piecewise.IManipulatorEngine;

public class ManipulatorRTreeStream implements IGeneratorFunction {

	private IManipulatorEngine engine;
	
	@Override
	public Function gerFuntionEdge(long idEdge, long timestamp) {
		return engine.run(timestamp);
	}
}
