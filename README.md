# VCOPT

An Android app for reading, writing, and generating [OpenPrintTag](https://specs.openprinttag.org) NFC tags for 3D printing materials.

## About

VCOPT helps you create and manage NFC tags that store metadata about 3D printing materials like filament spools. Tags encode information such as brand, material type, printing temperatures, colors, and certifications using the OpenPrintTag specification.

## Features

- **Read** - Scan NFC tags to view material information
- **Write** - Write generated or imported tag data to NFC tags
- **Generate** - Create tags with comprehensive material configuration (brand, type, temperatures, colors, certifications, and more)
- **Usage Tracking** - Write to the aux region only to track filament consumption without rewriting the full tag
- **Import/Export** - Save and load tag data as .bin files

## Spec Conformance

This app aims to conform to the OpenPrintTag specification, but the implementation may not be perfect. If you encounter any issues or inconsistencies with the spec, please open an issue.

## License

See [LICENSE](LICENSE) for details.
