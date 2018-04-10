package NUDT.module.algorithm.pathplanning.pov;

import java.awt.Point;

import NUDT.module.algorithm.pathplanning.pov.graph.PointNode;

/**
 * The cost from current node to its successor node.
 */
public interface CostFunction {

	double cost(PointNode from, PointNode to, Point startPoint);
}
