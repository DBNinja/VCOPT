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

## Screenshots
<img width="240" height="536" alt="Screenshot_20260116-085541" src="https://github.com/user-attachments/assets/924b0e65-e3d3-4333-ac89-2855aa795637" />
<img width="240" height="536" alt="Screenshot_20260116-085552" src="https://github.com/user-attachments/assets/133e6c5f-8e20-4b69-8eaa-bbd11b5f3c05" />
<img width="240" height="536" alt="Screenshot_20260116-085605" src="https://github.com/user-attachments/assets/3cc44144-3097-48cc-a62a-c0169ea9734d" />



## Spec Conformance

This app aims to conform to the OpenPrintTag specification, but the implementation may not be perfect. If you encounter any issues or inconsistencies with the spec, please open an issue.

## License

See [LICENSE](LICENSE) for details.
