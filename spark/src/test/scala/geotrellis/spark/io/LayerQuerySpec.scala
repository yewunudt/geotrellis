package geotrellis.spark.io

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.vector._
import geotrellis.proj4._
import geotrellis.spark.tiling._
import geotrellis.spark.testfiles._

import org.scalatest._

class LayerQuerySpec extends FunSpec
  with TestEnvironment with TestFiles with Matchers {

  def spatialKeyBoundsKeys(kb: KeyBounds[SpatialKey]) = {
    for {
      row <- kb.minKey.row to kb.maxKey.row
      col <- kb.minKey.col to kb.maxKey.col
    } yield SpatialKey(col, row)
  }

  describe("RasterQuerySpec") {
    val keyBounds = KeyBounds(SpatialKey(1, 1), SpatialKey(6, 7))

    val md = TileLayerMetadata(
      FloatConstantNoDataCellType,
      LayoutDefinition(LatLng.worldExtent, TileLayout(8, 8, 3, 4)),
      Extent(-135.00000125, -89.99999, 134.99999125, 67.49999249999999),
      LatLng,
      keyBounds
    )



    it("should be better then Java serialization") {
      val query = new LayerQuery[SpatialKey, TileLayerMetadata[SpatialKey]].where(Intersects(GridBounds(2, 2, 2, 2)))
      val outKeyBounds = query(md)
      info(outKeyBounds.toString)
    }

    it("should throw on intersecting regions") {
      val query = new LayerQuery[SpatialKey, TileLayerMetadata[SpatialKey]]
        .where(Intersects(GridBounds(2, 2, 2, 2)) or Intersects(GridBounds(2, 2, 2, 2)))

      intercept[RuntimeException] {
        query(md)
      }
    }

  }

  describe("LayerFilter Polygon Intersection") {
    import geotrellis.vector.{Point, Polygon, MultiPolygon}

    val md = AllOnesTestFile.metadata
    val mt = md.mapTransform
    val kb = KeyBounds[SpatialKey](SpatialKey(0, 0), SpatialKey(6, 7))
    val bounds = GridBounds(1, 1, 3, 2)
    val horizontal = Polygon(List(
      Point(-130.0, 60.0),
      Point(-130.0, 30.0),
      Point(-100.0, 30.0),
      Point(-100.0, 60.0),
      Point(-130.0, 60.0)))
    val vertical = Polygon(List(
      Point(-130.0, 40.0),
      Point(-130.0, 30.0),
      Point(-10.0, 30.0),
      Point(-10.0, 40.0),
      Point(-130.0, 40.0)))
    val diagonal = Polygon(List(
      Point(-125.0, 60.0),
      Point(-130.0, 55.0),
      Point(-15.0, 30.0),
      Point(-10.0, 35.0),
      Point(-125.0, 60.0)))

    def naiveKeys(polygon : MultiPolygon) = {
      (for ((x, y) <- bounds.coords
        if (polygon.intersects(md.mapTransform(SpatialKey(x, y))))) yield SpatialKey(x, y))
        .toList
    }

    it("should find all keys that intersect appreciably with a horizontal rectangle") {
      val polygon = MultiPolygon(horizontal)
      val query = new LayerQuery[SpatialKey, TileLayerMetadata[SpatialKey]].where(Intersects(polygon))
      val actual = query(md).flatMap(spatialKeyBoundsKeys)
      val expected = naiveKeys(polygon)
      (expected diff actual) should be ('empty)
    }

    it("should find all keys that intersect appreciably with a vertical rectangle") {
      val polygon = MultiPolygon(vertical)
      val query = new LayerQuery[SpatialKey, TileLayerMetadata[SpatialKey]].where(Intersects(polygon))
      val actual = query(md).flatMap(spatialKeyBoundsKeys)
      val expected = naiveKeys(polygon)
      (expected diff actual) should be ('empty)
    }

    it("should find all keys that intersect appreciably with an L-shaped polygon") {
      val polygon = MultiPolygon(List(horizontal, vertical))
      val query = new LayerQuery[SpatialKey, TileLayerMetadata[SpatialKey]].where(Intersects(polygon))
      val actual = query(md).flatMap(spatialKeyBoundsKeys)
      val expected = naiveKeys(polygon)
      (expected diff actual) should be ('empty)
    }

    it("should find all keys that intersect appreciably with a diagonal rectangle") {
      val polygon = MultiPolygon(diagonal)
      val query = new LayerQuery[SpatialKey, TileLayerMetadata[SpatialKey]].where(Intersects(polygon))
      val actual = query(md).flatMap(spatialKeyBoundsKeys)
      val expected = naiveKeys(polygon)
      (expected diff actual) should be ('empty)
    }
  }

  describe("LayerQuery KeyBounds generation") {
    val md = AllOnesTestFile.metadata
    val kb = KeyBounds[SpatialKey](SpatialKey(0, 0), SpatialKey(6, 7))

    it("should generate KeyBounds for single region") {
      val bounds1 = GridBounds(1, 1, 3, 2)
      val query = new LayerQuery[SpatialKey, TileLayerMetadata[SpatialKey]].where(Intersects(bounds1))
      val expected = for ((x, y) <- bounds1.coords) yield SpatialKey(x, y)

      val found = query(md).flatMap(spatialKeyBoundsKeys)
      info(s"missing: ${(expected diff found).toList}")
      info(s"unwanted: ${(found diff expected).toList}")

      found should contain theSameElementsAs expected
    }

    it("should generate KeyBounds for two regions") {
      val bounds1 = GridBounds(1, 1, 3, 3)
      val bounds2 = GridBounds(4, 5, 6, 6)
      val query = new LayerQuery[SpatialKey, TileLayerMetadata[SpatialKey]].where(Intersects(bounds1) or Intersects(bounds2))
      val expected = for ((x, y) <- bounds1.coords ++ bounds2.coords) yield SpatialKey(x, y)

      val found = query(md).flatMap(spatialKeyBoundsKeys)
      info(s"missing: ${(expected diff found).toList}")
      info(s"unwanted: ${(found diff expected).toList}")

      found should contain theSameElementsAs expected
    }
    // TODO: it would be nice to test SpaceTime too, but since time doesn't have a resolution we can not iterate
  }
}
