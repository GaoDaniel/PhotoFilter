import {Image, View, TouchableOpacity} from 'react-native';
import React, {useState} from 'react';
import {styles} from "../TabTwoScreen";
import * as ImageManipulator from "expo-image-manipulator";
import {FlipType} from "expo-image-manipulator";
import * as Progress from "react-native-progress";

export default function Transform(props: { callback: (b64Result: string) => void, disabled: boolean, curr: string }) {

  const [loadingTransform, setLoadingTransform] = useState<boolean>(false);

  /**
   * Transforms image
   */
  async function applyTransform(valueT: string) {
    setLoadingTransform(true);
    let result = null;
    const uri = 'data:image/jpeg;base64,' + props.curr;
    if (valueT == 'rotateCCW') {
      result = await ImageManipulator.manipulateAsync(uri, [{rotate: -90}], {base64: true});
    } else if (valueT == 'rotateCW') {
      result = await ImageManipulator.manipulateAsync(uri, [{rotate: 90}], {base64: true});
    } else if (valueT == 'vflip') {
      result = await ImageManipulator.manipulateAsync(uri, [{flip: FlipType.Vertical}], {base64: true});
    } else if (valueT == 'hflip') {
      result = await ImageManipulator.manipulateAsync(uri, [{flip: FlipType.Horizontal}], {base64: true});
    }
    if (result && result.base64) {
      props.callback(result.base64);
    }
    setLoadingTransform(false);
  }

  return (
    <View>
      <View style={styles.rowContainer}>
        <TouchableOpacity
          onPress={() => {
            applyTransform('rotateCCW')
          }}
          style={props.disabled || loadingTransform ? styles.disabledButton : styles.button}
          disabled={props.disabled || loadingTransform}>
          <Image source={require('../../assets/images/ccwRot.png')}
                 style={[styles.buttonImage, {width: 25, height: 25}]}/>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={() => {
            applyTransform('rotateCW')
          }}
          style={props.disabled ? styles.disabledButton : styles.button}
          disabled={props.disabled}>
          <Image source={require('../../assets/images/cwRot.png')}
                 style={[styles.buttonImage, {width: 25, height: 25}]}/>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={() => {
            applyTransform('vflip')
          }}
          style={props.disabled ? styles.disabledButton : styles.button}
          disabled={props.disabled}>
          <Image source={require('../../assets/images/vflip.png')}
                 style={[styles.buttonImage, {width: 25, height: 25}]}/>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={() => {
            applyTransform('hflip')
          }}
          style={props.disabled ? styles.disabledButton : styles.button}
          disabled={props.disabled}>
          <Image source={require('../../assets/images/hflip.png')}
                 style={[styles.buttonImage, {width: 25, height: 25}]}/>
        </TouchableOpacity>


      </View>
      <View style={styles.rowContainer}>
        {loadingTransform && <Progress.Bar width={350} indeterminate={loadingTransform}/>}
      </View>
    </View>
  );
}