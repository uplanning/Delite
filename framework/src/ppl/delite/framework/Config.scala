package ppl.delite.framework

object Config {

  private def getProperty(prop: String, default: String) = {
    val p1 = System.getProperty(prop)
    val p2 = System.getProperty(prop.substring(1))
    if (p1 != null && p2 != null) {
      assert(p1 == p2, "ERROR: conflicting properties")
      p1
    }
    else if (p1 != null) p1 else if (p2 != null) p2 else default
  }

  //var degFilename = System.getProperty("delite.deg.filename", "")
  var degFilename = getProperty("delite.deg.filename", "out.deg")
  var opfusionEnabled = getProperty("delite.enable.fusion", "true") != "false"
  var generateCUDA = getProperty("delite.generate.cuda", "false") != "false"
  var generateC = getProperty("delite.generate.c", "false") != "false"
  var generateOpenCL = getProperty("delite.generate.opencl", "false") != "false"
  var homeDir = getProperty("delite.home.dir", System.getProperty("user.dir"))
  var buildDir = getProperty("delite.build.dir", System.getProperty("user.dir") + java.io.File.separator + "generated" +
      java.io.File.separator + degName)
  var useBlas = getProperty("delite.extern.blas", "false") != "false"
  var nestedVariantsLevel = getProperty("nested.variants.level", "0").toInt
  var debug = getProperty("delite.debug","false") != "false"

  //Print generationFailedException info
  val dumpException: Boolean = getProperty("delite.dump.exception", "false") != "false"
  var enableProfiler = System.getProperty("delite.enable.profiler", "false") != "false"
  val profileDir = System.getProperty("delite.profiler.dir", buildDir+ java.io.File.separator + "profile")

  def degName = degFilename.substring(0, degFilename.length() - 4)
}
