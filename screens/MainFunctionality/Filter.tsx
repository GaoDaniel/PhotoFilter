import {View, TouchableOpacity, Text} from 'react-native';
import React, {useEffect, useState} from 'react';
import {styles} from "../TabTwoScreen";
import * as Progress from 'react-native-progress';
import ColorPicker from "react-native-wheel-color-picker";
import Slider from "@react-native-community/slider";
import DropDownPicker from "react-native-dropdown-picker";
import Constants from "expo-constants";

const {manifest} = Constants;

export default function Filter(
  props: { platform: string, curr: string, callback: (b64: string) => void, makeAlert: (s1: string, s2: string) => void }) {

  // get domain of spark server (needed for connection from devices not running Spark server)
  let mobileDomain: string;
  if (manifest && typeof manifest.debuggerHost === 'string') {
    mobileDomain = (typeof manifest.packagerOpts === `object`) && manifest.packagerOpts.dev
      ? manifest.debuggerHost.split(`:`)[0].concat(`:4567`)
      : `api.example.com`;
  }

  // filter state
  const [openF, setOpen] = useState(false);
  const [valueF, setValue] = useState('');
  const [itemsF, setItems] = useState([
    {label: 'Colors', value: 'colors'},
    {label: 'Invert', value: 'invert', parent: 'colors'},
    {label: 'Grayscale', value: 'gray', parent: 'colors'},
    {label: 'Black and White', value: 'bw', parent: 'colors'},
    {label: 'Color', value: 'color', parent: 'colors'},

    {label: 'Funny', value: 'funny'},
    {label: 'Emojify', value: 'emoji', parent: 'funny'},
    {label: 'Asciify', value: 'ascii', parent: 'funny'},
    {label: 'Ansify', value: 'ansi', parent: 'funny'},

    {label: 'Classic', value: 'classic'},
    {label: 'Box Blur', value: 'box', parent: 'classic'},
    {label: 'Gaussian Blur', value: 'gauss', parent: 'classic'},
    {label: 'Sharpen', value: 'sharp', parent: 'classic'},
    {label: 'Remove Noise', value: 'noise', parent: 'classic'},
    {label: 'Brightness', value: 'bright', parent: 'classic'},
    {label: 'Saturation', value: 'sat', parent: 'classic'},
    {label: 'Outline', value: 'outline', parent: 'classic'},
    // was going to make this use outline to thicken/thin the outline of objects in an image
    // {label: 'Outliner', value: 'outliner', parent: 'classic'},
    {label: 'Dominant Hue', value: 'dom', parent: 'classic'},

    {label: 'Test', value: 'test'},
    // will need to add Test Filter classes on SparkServer side
    // {label: 'Test1', value: 'test1', parent: 'test'},
    // {label: 'Test2', value: 'test2', parent: 'test'},
    // {label: 'Test3', value: 'test3', parent: 'test'},
  ]);

  const sliderFilters: Set<String> = new Set<String>(['box', 'gauss', 'sharp', 'bright', 'sat', 'red', 'green',
    'blue', 'cyan', 'magenta', 'yellow', 'test1', 'test2', 'test3', 'dom', 'outliner', 'bw']);
  const zto100Filters: Set<String> = new Set<String>(['box', 'gauss', 'sharp', 'test1', 'test2', 'test3']);

  // Slider state
  const [valueS, setValueS] = useState(0);

  // Wheel state
  const [wColor, setColor] = useState('#fff');
  const [loadingFilter, setLoadingFilter] = useState<boolean>(false);

  /**
   * applies filter by calling Spark server
   */
  async function applyFilter() {
    console.log(props.curr);
    setLoadingFilter(true);
    try {
      let domain = props.platform !== "web" ? mobileDomain : "localhost:4567";
      let response = await fetch("http://" + domain + "/filtering?filter=" + valueF + "&int=" + valueS + "&c=" + wColor.substring(1), {
        method: 'POST',
        body: props.curr,
      });

      if (!response.ok) {
        props.makeAlert("Status Wrong!", "The status is wrong! Expected: 200, was: " + response.status);
        setLoadingFilter(false);
        return;
      }

      let object = await response.json();
      props.callback(object.toString());
      setLoadingFilter(false);
    } catch (e) {
      setLoadingFilter(false);
      props.makeAlert("Server Error", "There was an error contacting the server",)
      console.log(e);
    }
  }

  useEffect(() => {
    console.log("Filter changed");
    setValueS(0);
  }, [valueF])

  return (
    <View>
      <View style={[styles.rowContainer]}>
        <TouchableOpacity
          onPress={applyFilter}
          style={props.curr === undefined || valueF === '' || loadingFilter ?
            styles.disabledButton : [styles.button, {backgroundColor: 'darkorchid'}]}
          disabled={props.curr === undefined || valueF === '' || loadingFilter}>
          <Text style={styles.buttonText}>Apply Filter</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.rowContainer}>
        {loadingFilter && <Progress.Bar width={350} indeterminate={loadingFilter}/>}
      </View>

      <View style={styles.rowContainer}>
        {valueF == 'color' &&
            <ColorPicker
                sliderSize={30}
                gapSize={10}
                discrete={false}
                swatches={false}
                autoResetSlider={false}
                shadeWheelThumb={false}
                shadeSliderThumb={true}
                onColorChangeComplete={(color) => {
                  setColor(color)
                }}
            />}
      </View>

      <View style={styles.rowContainer}>
        {sliderFilters.has(valueF) &&
            <Slider style={[styles.button, {flex: 5}]}
                    minimumValue={zto100Filters.has(valueF) ? 0 : -100}
                    maximumValue={100}
                    step={1}
                    value={valueS}
                    onValueChange={setValueS}
                    minimumTrackTintColor="#000000"
                    maximumTrackTintColor="#000000"
                    thumbTintColor='magenta'
                    disabled={props.curr === undefined || valueF === '' || loadingFilter}
                    onSlidingComplete={setValueS}
                    tapToSeek={true}
            />}
        {sliderFilters.has(valueF) &&
            <View style={[styles.button, {flex: 1}]}>
                <Text style={styles.buttonText}>{valueS}</Text>
            </View>
        }
      </View>


      <View style={[styles.rowContainer, {zIndex: 1,}]}>
        <DropDownPicker
          open={openF}
          multiple={false}
          value={valueF}
          items={itemsF}
          setOpen={setOpen}
          setValue={setValue}
          setItems={setItems}

          placeholder="Select a Filter"
          listMode="SCROLLVIEW"
          autoScroll={true}
          style={[styles.button, {flexDirection: 'row'}]}
          textStyle={styles.dropText}
          dropDownContainerStyle={{backgroundColor: "#fff", maxHeight: 500}}
          stickyHeader={false}
          categorySelectable={false}
          listItemLabelStyle={{color: "#000"}}
          listParentLabelStyle={styles.dropdownParent}
          listChildContainerStyle={styles.dropdownItem}
          listChildLabelStyle={styles.dropdownText}
          selectedItemLabelStyle={{fontWeight: "bold", color: '#fff'}}
          selectedItemContainerStyle={{backgroundColor: "darkorchid"}}
          tickIconStyle={{width: 20, height: 20}}
        />
      </View>
    </View>
  );
}
