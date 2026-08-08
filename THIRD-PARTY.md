# Third-party notices

The `openexr-ffm-natives` classifier jars contain shared libraries built from
unmodified upstream source. Exact versions, source URLs, and build options are
also recorded in each jar's `BUILD-INFO.txt`.

## OpenEXR and Imath

OpenEXR 3.4.14 and Imath 3.2.2 are Academy Software Foundation projects
licensed under BSD-3-Clause. Their license texts ship as `LICENSE.OpenEXR` and
`LICENSE.Imath`; OpenEXR's additional patent grant ships as `PATENTS.OpenEXR`.

Sources:

- https://github.com/AcademySoftwareFoundation/openexr/tree/v3.4.14
- https://github.com/AcademySoftwareFoundation/Imath/tree/v3.2.2

## Bundled compression dependencies

The release-vendored libdeflate implementation is statically incorporated into
OpenEXRCore under the MIT license (`LICENSE.libdeflate`). The release-vendored
OpenJPH implementation is statically incorporated under BSD-2-Clause
(`LICENSE.OpenJPH`). No separate shared libraries are shipped for either.

The Java binding and loader code contains no OpenEXR source and is MIT licensed
under this repository's `LICENSE`.

