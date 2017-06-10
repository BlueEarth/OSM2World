package org.osm2world.core.world.modules;

import static java.lang.Math.cos;
import static org.osm2world.core.target.common.material.Materials.*;
import static org.osm2world.core.target.common.material.TexCoordUtil.triangleTexCoordLists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.osm2world.core.map_data.data.MapArea;
import org.osm2world.core.map_elevation.data.GroundState;
import org.osm2world.core.math.SimplePolygonXZ;
import org.osm2world.core.math.TriangleXYZ;
import org.osm2world.core.math.VectorXYZ;
import org.osm2world.core.math.VectorXZ;
import org.osm2world.core.math.algorithms.PolygonUtil;
import org.osm2world.core.target.RenderableToAllTargets;
import org.osm2world.core.target.Target;
import org.osm2world.core.target.common.TextureData;
import org.osm2world.core.target.common.material.Material;
import org.osm2world.core.target.common.material.NamedTexCoordFunction;
import org.osm2world.core.target.common.material.TexCoordFunction;
import org.osm2world.core.world.data.AbstractAreaWorldObject;
import org.osm2world.core.world.data.TerrainBoundaryWorldObject;
import org.osm2world.core.world.modules.common.AbstractModule;

/**
 * adds pitches for various sports to the map
 */
public class SportsModule extends AbstractModule {
	
	@Override
	public void applyToArea(MapArea area) {
				
		if (area.getTags().contains("leisure", "pitch")) {
			
			String sport = area.getTags().getValue("sport");
			
			if ("soccer".equals(sport)) {
				area.addRepresentation(new SoccerPitch(area));
			}
			
		}
		
	}
	
	/**
	 * a pitch with markings for any sport
	 */
	static abstract class Pitch extends AbstractAreaWorldObject
			implements RenderableToAllTargets, TerrainBoundaryWorldObject {
	
		public Pitch(MapArea area) {
			
			super(area);
			
		}

		/** minimum length of the pitch's longer side, in meters */
		protected abstract double getMinLongSide();
		
		/** maximum length of the pitch's longer side, in meters */
		protected abstract double getMaxLongSide();
		
		/** minimum length of the pitch's shorter side, in meters */
		protected abstract double getMinShortSide();
		
		/** maximum length of the pitch's shorter side, in meters */
		protected abstract double getMaxShortSide();
		
		/** the regular material for the pitch */
		protected abstract Material getPitchMaterial();
		
		/**
		 * the fallback material to be used instead of {@link #getPitchMaterial()}
		 * if no legal pitch can be constructed
		 */
		protected abstract Material getFallbackPitchMaterial();
		
		@Override
		public GroundState getGroundState() {
			return GroundState.ON;
		}
		
		@Override
		public void renderTo(Target<?> target) {

			Collection<TriangleXYZ> triangles = getTriangulation();
			
			TexCoordFunction texFunction = configureTexFunction(area.getOuterPolygon());

			if (texFunction != null) {
				
				Material material = getPitchMaterial();
				
				target.drawTriangles(material, triangles,
						triangleTexCoordLists(triangles, material, texFunction));
				
			} else {

				Material material = getFallbackPitchMaterial();
				
				target.drawTriangles(material, triangles,
						triangleTexCoordLists(triangles, material, NamedTexCoordFunction.GLOBAL_X_Z));
				
			}
			
		}

		/**
		 * calculates the parameters for a {@link TexCoordFunction}
		 * and calls the constructor
		 * 
		 * @return  the texture coordinate function;
		 * null if it's not possible to construct a valid pitch
		 */
		private TexCoordFunction configureTexFunction(SimplePolygonXZ polygon) {
			
			/* approximate a rectangular shape for the pitch */
			
			SimplePolygonXZ bbox = PolygonUtil.minimumBoundingBox(polygon);

			VectorXZ origin = bbox.getVertex(0);
			
			VectorXZ longSide, shortSide;
			
			if (bbox.getVertex(0).distanceTo(bbox.getVertex(1))
					> bbox.getVertex(1).distanceTo(bbox.getVertex(2))) {
				
				longSide = bbox.getVertex(1).subtract(bbox.getVertex(0));
				shortSide = bbox.getVertex(2).subtract(bbox.getVertex(1));
				
			} else {

				longSide = bbox.getVertex(2).subtract(bbox.getVertex(1));
				shortSide = bbox.getVertex(1).subtract(bbox.getVertex(0));
				
			}
			
			/* scale the pitch markings based on official regulations (TODO use values from config file) */

			double newLongSideLength = longSide.length() * 0.95;
			double newShortSideLength = shortSide.length() * 0.95;
			
			if (newLongSideLength < getMinLongSide()) {
				return null;
			} else if (newLongSideLength > getMaxLongSide()) {
				newLongSideLength = getMaxLongSide();
			}
			
			if (newShortSideLength < getMinShortSide()) {
				return null;
			} else if (newShortSideLength > getMaxShortSide()) {
				newShortSideLength = getMaxShortSide();
			}
						
			origin = origin
					.add(longSide.mult((longSide.length() - newLongSideLength) / 2 / longSide.length()))
					.add(shortSide.mult((shortSide.length() - newShortSideLength) / 2 / shortSide.length()));
			
			longSide = longSide.mult(newLongSideLength / longSide.length());
			shortSide = shortSide.mult(newShortSideLength / shortSide.length());
			
			/* build the result */
			
			return new PitchTexFunction(origin, longSide, shortSide);
			
		}

		/**
		 * specialized texture coordinate calculation for pitch marking textures
		 */
		static class PitchTexFunction implements TexCoordFunction {
			
			private final VectorXZ origin;
			private final VectorXZ longSide;
			private final VectorXZ shortSide;
			
			PitchTexFunction(VectorXZ origin, VectorXZ longSide, VectorXZ shortSide) {
				
				this.origin = origin;
				this.longSide = longSide;
				this.shortSide = shortSide;
				
			}

			@Override
			public List<VectorXZ> apply(List<VectorXYZ> vs, TextureData textureData) {
				
				List<VectorXZ> result = new ArrayList<VectorXZ>(vs.size());
				
				for (VectorXYZ vOriginal : vs) {
					
					VectorXZ v = vOriginal.xz().subtract(origin);
					
					double angleLong = VectorXZ.angleBetween(v, longSide);
					double longSideProjectedLength = v.length() * cos(angleLong);
					
					double angleShort = VectorXZ.angleBetween(v, shortSide);
					double shortSideProjectedLength = v.length() * cos(angleShort);
					
					result.add(new VectorXZ(
							shortSideProjectedLength / shortSide.length(),
							longSideProjectedLength / longSide.length()));
					
				}
				
				return result;
				
			}
			
		}
		
	}

	/**
	 * a pitch with soccer markings
	 */
	class SoccerPitch extends Pitch {

		SoccerPitch(MapArea area) {
			super(area);
		}
		
		@Override
		protected double getMinLongSide() {
			return 90;
			
		}

		@Override
		protected double getMaxLongSide() {
			return 120;
		}

		@Override
		protected double getMinShortSide() {
			return 45;
		}

		@Override
		protected double getMaxShortSide() {
			return 90;
		}

		@Override
		protected Material getPitchMaterial() {
			return PITCH_SOCCER;
		}

		@Override
		protected Material getFallbackPitchMaterial() {
			return GRASS;
		}
		
	}
	
}
